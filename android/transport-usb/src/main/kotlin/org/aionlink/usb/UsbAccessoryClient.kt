package org.aionlink.usb

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.*
import android.system.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.aionlink.iccoa.*
import java.io.FileDescriptor
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class UsbStage { IDLE, WAITING_PERMISSION, OPENING, OBSERVING, COMPLETE, CLOSED, ERROR }
data class ObservedAccessory(val id: String, val label: String, val observedIccoa: Boolean, val permission: Boolean)
data class UsbSnapshot(
    val accessories: List<ObservedAccessory> = emptyList(), val hostDevices: Int = 0,
    val usbPower: Boolean? = null, val stage: UsbStage = UsbStage.IDLE,
    val bytesRead: Long = 0, val frames: Int = 0, val requestsSent: Int = 0,
    val knownPorts: Set<Int> = emptySet(), val message: String? = null
) { val busy get() = stage in setOf(UsbStage.WAITING_PERMISSION,UsbStage.OPENING,UsbStage.OBSERVING) }

/** Owner-selected AOA transport. No serial/URI/payload retention, privilege changes, or automatic writes. */
class UsbAccessoryClient(context: Context, private val event: (String) -> Unit) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(UsbManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateMutable = MutableStateFlow(UsbSnapshot())
    val state = stateMutable.asStateFlow()
    private val handles = mutableMapOf<String, UsbAccessory>()
    private val ids = mutableMapOf<UsbAccessory, String>()
    private val generation = AtomicInteger()
    private val descriptor = AtomicReference<ParcelFileDescriptor?>(null)
    private val descriptorLock = Any()
    private var work: Job? = null
    private var permissionTimeout: Job? = null
    private var target: UsbAccessory? = null
    private var bootstrap = false
    private var registered = false
    private var permissionIntent: PendingIntent? = null
    private val permissionAction = context.packageName + ".USB_PERMISSION." + UUID.randomUUID()

    private val attachment = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_ACCESSORY_DETACHED) {
                val selected = target
                if (selected != null && manager?.accessoryList?.contains(selected) != true) stop("Accesorio USB desconectado")
            }
            refresh()
        }
    }
    private val permission = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction || intent.getIntExtra("attempt",-1) != generation.get() || state.value.stage != UsbStage.WAITING_PERMISSION) return
            permissionTimeout?.cancel()
            // Do not trust supplied extras; verify actual permission on the selected live accessory.
            val selected = target
            if (selected == null || manager?.accessoryList?.contains(selected) != true || !manager.hasPermission(selected)) {
                stateMutable.update { it.copy(stage = UsbStage.ERROR, message = "Permiso USB no concedido o accesorio retirado") }
                event("USB: permiso no disponible")
                target = null
            } else begin(selected, bootstrap, generation.get())
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED); addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(attachment, filter, Context.RECEIVER_EXPORTED)
            context.registerReceiver(permission, IntentFilter(permissionAction), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(attachment, filter)
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permission, IntentFilter(permissionAction))
        }
        registered = true
        refresh()
    }

    fun refresh() {
        try {
            val live = manager?.accessoryList?.toList() ?: emptyList()
            ids.keys.retainAll(live.toSet())
            handles.clear()
            val accessories = live.map { accessory ->
                val id = ids.getOrPut(accessory) { UUID.randomUUID().toString().take(8) }
                handles[id] = accessory
                val recognized = UsbAccessoryIdentity.isObservedIccoa(accessory.manufacturer, accessory.model, accessory.version)
                ObservedAccessory(id, listOfNotNull(accessory.manufacturer,accessory.model,accessory.version).joinToString(" / "),
                    recognized, manager!!.hasPermission(accessory))
            }
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)
            stateMutable.update { it.copy(accessories = accessories, hostDevices = manager?.deviceList?.size ?: 0,
                usbPower = plugged?.takeIf { n -> n >= 0 }?.let { n -> n and BatteryManager.BATTERY_PLUGGED_USB != 0 }) }
        } catch (_: SecurityException) {
            stateMutable.update { it.copy(message = "Android restringió la consulta USB") }
        }
    }

    fun request(id: String, startObservedChannels: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val selected = handles[id]
        if (selected == null || manager?.accessoryList?.contains(selected) != true) {
            stateMutable.update { it.copy(stage = UsbStage.ERROR, message = "Accesorio ausente; actualiza USB") }; return
        }
        if (startObservedChannels && !UsbAccessoryIdentity.isObservedIccoa(selected.manufacturer,selected.model,selected.version)) {
            stateMutable.update { it.copy(stage = UsbStage.ERROR, message = "El inicio experimental exige ICCOA / CarLink / 1.0.0") }; return
        }
        stop()
        target = selected; bootstrap = startObservedChannels
        val ticket = generation.get()
        stateMutable.update { it.copy(bytesRead = 0,frames = 0,requestsSent = 0,knownPorts = emptySet(),message = null) }
        if (manager.hasPermission(selected)) begin(selected,bootstrap,ticket)
        else {
            stateMutable.update { it.copy(stage = UsbStage.WAITING_PERMISSION) }
            val pending = PendingIntent.getBroadcast(context, ticket, Intent(permissionAction).setPackage(context.packageName).putExtra("attempt",ticket),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            permissionIntent = pending
            try {
                manager.requestPermission(selected,pending)
                permissionTimeout = scope.launch {
                    delay(30000)
                    if (ticket == generation.get() && state.value.stage == UsbStage.WAITING_PERMISSION) stop("Autorización USB pendiente; vuelve a intentarlo")
                }
            } catch (_: SecurityException) { stop("Android rechazó solicitar permiso USB") }
        }
    }

    private fun publish(ticket: Int, update: (UsbSnapshot) -> UsbSnapshot) {
        stateMutable.update { if (generation.get() == ticket) update(it) else it }
    }

    private fun begin(accessory: UsbAccessory, startChannels: Boolean, ticket: Int) {
        permissionIntent?.cancel(); permissionIntent = null
        stateMutable.update { it.copy(stage = UsbStage.OPENING) }
        work = scope.launch(Dispatchers.IO) {
            var opened: ParcelFileDescriptor? = null
            try {
                opened = manager?.openAccessory(accessory) ?: error("Open returned null")
                synchronized(descriptorLock) {
                    if (ticket != generation.get()) return@launch
                    descriptor.set(opened)
                }
                val fd = opened.fileDescriptor
                Os.fcntlInt(fd, OsConstants.F_SETFL, Os.fcntlInt(fd, OsConstants.F_GETFL,0) or OsConstants.O_NONBLOCK)
                val deadline = SystemClock.elapsedRealtime() + 15000
                publish(ticket) { it.copy(stage = UsbStage.OBSERVING, message = "Descriptor AOA abierto; no es una sesión ICCOA") }
                if (startChannels) {
                    for (port in listOf(7236,4321)) {
                        val (header,body) = UsbChannelStartup.request(port)
                        write(fd,header,deadline,ticket); write(fd,body,deadline,ticket)
                        publish(ticket) { it.copy(requestsSent = it.requestsSent + 1) }
                    }
                }
                val decoder = UsbMuxDecoder()
                val chunk = ByteArray(16384)
                var bytesRead = 0L
                var frames = 0
                while (ticket == generation.get() && SystemClock.elapsedRealtime() < deadline && frames < 256 && bytesRead < 2L*1024*1024) {
                    ensureActive()
                    val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
                    if (Os.poll(arrayOf(poll),250) == 0) continue
                    val flags = poll.revents.toInt()
                    if (flags and OsConstants.POLLIN == 0) {
                        if (flags and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) break
                        continue
                    }
                    val count = try { Os.read(fd,chunk,0,chunk.size) } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EAGAIN) continue else throw e
                    }
                    if (count <= 0) break
                    bytesRead += count
                    publish(ticket) { it.copy(bytesRead = bytesRead) }
                    val packets = decoder.accept(chunk,count)
                    frames += packets.size
                    val ports = packets.mapNotNull { it.control?.knownPort }.toSet()
                    publish(ticket) { it.copy(bytesRead = bytesRead, frames = frames,knownPorts = it.knownPorts + ports) }
                }
                publish(ticket) { it.copy(stage = UsbStage.COMPLETE, message = if (decoder.hasPartialFrame)
                    "Lectura finalizada con frame incompleto; canal cerrado" else "Observación finalizada; canal cerrado. Sesión ICCOA pendiente") }
                withContext(Dispatchers.Main) { if (ticket == generation.get()) event("USB: observación terminada; frames=$frames; sesión sin verificar") }
            } catch (e: CancellationException) { throw e }
            catch (e: ErrnoException) { publish(ticket) { it.copy(stage = UsbStage.ERROR, message = "Error de E/S AOA (errno ${e.errno}); canal cerrado") } }
            catch (_: SecurityException) { publish(ticket) { it.copy(stage = UsbStage.ERROR, message = "Permiso USB revocado") } }
            catch (_: IllegalArgumentException) { publish(ticket) { it.copy(stage = UsbStage.ERROR, message = "Framing USB fuera de los límites de análisis") } }
            catch (_: Exception) { publish(ticket) { it.copy(stage = UsbStage.ERROR, message = "No se pudo usar el canal AOA; desconexión, modo no compatible o canal ocupado") } }
            finally {
                if (opened != null) { descriptor.compareAndSet(opened,null); try { opened.close() } catch (_: Exception) { } }
            }
        }
    }

    private fun write(fd: FileDescriptor, bytes: ByteArray, deadline: Long, ticket: Int) {
        var offset = 0
        while (offset < bytes.size) {
            check(ticket == generation.get() && SystemClock.elapsedRealtime() < deadline) { "USB write cancelled" }
            val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLOUT.toShort() }
            if (Os.poll(arrayOf(poll),250) == 0) continue
            val count = try { Os.write(fd,bytes,offset,bytes.size-offset) } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) continue else throw e
            }
            check(count > 0); offset += count
        }
    }

    fun stop(message: String? = null) {
        val link = synchronized(descriptorLock) {
            generation.incrementAndGet()
            descriptor.getAndSet(null)
        }
        permissionTimeout?.cancel(); permissionTimeout = null
        permissionIntent?.cancel(); permissionIntent = null
        work?.cancel(); work = null
        link?.let { try { it.close() } catch (_: Exception) { } }
        target = null
        if (state.value.busy || message != null) stateMutable.update { it.copy(stage = UsbStage.CLOSED,message = message ?: "Canal USB cerrado") }
    }
    fun close() {
        stop(); scope.cancel()
        if (registered) { context.unregisterReceiver(attachment); context.unregisterReceiver(permission); registered = false }
    }
}
