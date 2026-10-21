# Uso y límites de herramientas

No hace falta compilar Apktool desde fuentes: no se encontró tal repositorio y se
usó el JAR oficial verificado. `scripts/Prepare-Tools.ps1` fija versiones y hashes,
rechaza discrepancias y conserva archivos existentes. No instala globalmente.

`analyze_apks.py` busca originales en raíz y apks/original, asigna carpetas por
nombre/hash y conserva los originales. `--skip-jadx` refresca inventario sin repetir
decompilación; `--apk <ruta-local>` limita el análisis a una entrada. No ejecutar
dos análisis sobre la misma entrada a la vez. Si encuentra extracción parcial,
la conserva para revisión en vez de sobrescribirla. Los exit codes y logs se
guardan por entrada; un resultado parcial no se convierte en éxito.

Para métodos que JADX no reconstruye, inspeccionar primero el Smali. Para una clase
con salida raw separada (sigue siendo material propietario local):

```powershell
python scripts/decompile_class.py <apk-local> nombre.completo.Clase
```

Usa modo fallback, un worker y heap 1 GiB, con timeout de tres minutos y resultado
JSON. No sobrescribe una ejecución anterior. No interpretar sus instrucciones raw
como código fuente original o como implementación lista para copiar.

Después del inventario y ELF: `python scripts/generate_reports.py` actualiza dos
resúmenes sin incluir código descompilado. Para comparar versiones:
`python scripts/compare_apks.py <metadata-anterior.json> <metadata-nuevo.json>`.
Los resultados pueden contener permisos/componentes; revisar antes de publicar.

No confundir un aviso de decompilación con un bug del binario original. Una biblioteca
con extensión .so puede contener datos en otro formato. No renombrar o ejecutar esos
archivos para probarlos. El inventario completo es local y queda excluido de Git.
