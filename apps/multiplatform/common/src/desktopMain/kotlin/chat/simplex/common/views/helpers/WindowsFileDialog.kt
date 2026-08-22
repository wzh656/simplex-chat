package chat.simplex.common.views.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.FrameWindowScope
import chat.simplex.common.DialogParams
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.res.MR
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import java.io.File

private const val FOS_OVERWRITEPROMPT = 0x2
private const val FOS_PICKFOLDERS = 0x20
private const val FOS_FORCEFILESYSTEM = 0x40
private const val FOS_ALLOWMULTISELECT = 0x200
private const val FOS_PATHMUSTEXIST = 0x800
private const val FOS_FILEMUSTEXIST = 0x1000
private const val SIGDN_FILESYSPATH = 0x80058000L

@Composable
internal fun FrameWindowScope.WindowsFileDialogChooser(
  title: String,
  isLoad: Boolean,
  params: DialogParams,
  onResult: (List<File>) -> Unit
) {
  LaunchedEffect(Unit) {
    val result = try {
      WindowsFileDialog.show(window, title, isLoad, params)
    } catch (e: Throwable) {
      Log.e(TAG, "Unable to show Windows file dialog: ${e.stackTraceToString()}")
      AlertManager.shared.showAlertMsg(
        title = generalGetString(MR.strings.error),
        text = e.message ?: generalGetString(MR.strings.unknown_error)
      )
      emptyList()
    }
    onResult(result)
  }
}

private object WindowsFileDialog {
  suspend fun show(owner: Window, title: String, isLoad: Boolean, params: DialogParams): List<File> =
    withContext(Dispatchers.IO) {
      val selectDirectory = !isLoad && params.filename == null
      val openDialog = isLoad || selectDirectory
      val dialog = createDialog(openDialog)
      try {
        dialog.setTitle(title)
        dialog.addOptions(
          FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or when {
            selectDirectory -> FOS_PICKFOLDERS
            isLoad -> FOS_FILEMUSTEXIST or if (params.allowMultiple) FOS_ALLOWMULTISELECT else 0
            else -> FOS_OVERWRITEPROMPT
          }
        )

        if (!isLoad) {
          params.filename?.takeIf(String::isNotEmpty)?.let { filename ->
            dialog.setFileName(filename)
            filename.substringAfterLast('.', "").takeIf(String::isNotEmpty)?.let { extension ->
              dialog.setDefaultExtension(extension)
              dialog.setFileTypes(params.fileFilterDescription.ifEmpty { "*.$extension" }, setOf(extension))
            }
          }
        } else {
          params.fileExtensions.takeIf(Set<String>::isNotEmpty)?.let { extensions ->
            dialog.setFileTypes(params.fileFilterDescription, extensions)
          }
        }

        if (!dialog.show(owner)) return@withContext emptyList()

        val selected = if (openDialog && params.allowMultiple) {
          (dialog as NativeFileOpenDialog).results()
        } else {
          listOf(dialog.result())
        }
        if (isLoad) {
          selected.filter { it.canRead() && (params.fileFilter?.invoke(it) != false) }
        } else {
          selected
        }
      } finally {
        dialog.Release()
        Ole32.INSTANCE.CoUninitialize()
      }
    }

  private fun createDialog(open: Boolean): NativeFileDialog {
    val initialized = Ole32.INSTANCE.CoInitializeEx(
      null,
      Ole32.COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE
    )
    initialized.verify("CoInitializeEx failed")

    try {
      val pointer = PointerByReference()
      val result = Ole32.INSTANCE.CoCreateInstance(
        if (open) FileOpenDialogClsid else FileSaveDialogClsid,
        null,
        WTypes.CLSCTX_INPROC_SERVER,
        if (open) FileOpenDialogIid else FileSaveDialogIid,
        pointer
      )
      result.verify("CoCreateInstance failed")
      return if (open) NativeFileOpenDialog(pointer.value) else NativeFileDialog(pointer.value)
    } catch (e: Throwable) {
      Ole32.INSTANCE.CoUninitialize()
      throw e
    }
  }
}

private open class NativeFileDialog(pointer: Pointer) : Unknown(pointer) {
  fun show(owner: Window): Boolean {
    val hwnd = WinDef.HWND(Native.getWindowPointer(owner))
    val result = invokeResult(3, hwnd)
    if (result.toInt() == Win32Exception(WinError.ERROR_CANCELLED).hr.toInt()) return false
    result.verify("IFileDialog.Show failed")
    return true
  }

  fun setFileTypes(description: String, extensions: Set<String>) {
    val pattern = extensions.joinToString(";") { "*.$it" }
    val spec = ComdlgFilterSpec().apply {
      pszName = WString(description.ifBlank { pattern })
      pszSpec = WString(pattern)
    }
    invokeResult(4, 1, arrayOf<ComdlgFilterSpec?>(spec)).verify("IFileDialog.SetFileTypes failed")
  }

  fun addOptions(options: Int) {
    val current = IntByReference()
    invokeResult(10, current).verify("IFileDialog.GetOptions failed")
    invokeResult(9, current.value or options).verify("IFileDialog.SetOptions failed")
  }

  fun setFileName(filename: String) {
    invokeResult(15, WString(filename)).verify("IFileDialog.SetFileName failed")
  }

  fun setTitle(title: String) {
    invokeResult(17, WString(title)).verify("IFileDialog.SetTitle failed")
  }

  fun result(): File {
    val itemPointer = PointerByReference()
    invokeResult(20, itemPointer).verify("IFileDialog.GetResult failed")
    return NativeShellItem(itemPointer.value).use { it.file() }
  }

  fun setDefaultExtension(extension: String) {
    invokeResult(22, WString(extension)).verify("IFileDialog.SetDefaultExtension failed")
  }

  protected fun invokeResult(index: Int, vararg arguments: Any?): WinNT.HRESULT =
    _invokeNativeObject(
      index,
      arrayOf(pointer, *arguments),
      WinNT.HRESULT::class.java
    ) as WinNT.HRESULT
}

private class NativeFileOpenDialog(pointer: Pointer) : NativeFileDialog(pointer) {
  fun results(): List<File> {
    val arrayPointer = PointerByReference()
    invokeResult(27, arrayPointer).verify("IFileOpenDialog.GetResults failed")
    return NativeShellItemArray(arrayPointer.value).use { items ->
      val count = items.count()
      List(count) { index -> items.item(index).use { it.file() } }
    }
  }
}

private class NativeShellItem(pointer: Pointer) : Unknown(pointer), AutoCloseable {
  fun file(): File {
    val displayName = PointerByReference()
    val result = _invokeNativeObject(
      5,
      arrayOf(pointer, SIGDN_FILESYSPATH, displayName),
      WinNT.HRESULT::class.java
    ) as WinNT.HRESULT
    result.verify("IShellItem.GetDisplayName failed")
    return try {
      File(displayName.value.getWideString(0))
    } finally {
      Ole32.INSTANCE.CoTaskMemFree(displayName.value)
    }
  }

  override fun close() {
    Release()
  }
}

private class NativeShellItemArray(pointer: Pointer) : Unknown(pointer), AutoCloseable {
  fun count(): Int {
    val count = IntByReference()
    val result = _invokeNativeObject(
      7,
      arrayOf(pointer, count),
      WinNT.HRESULT::class.java
    ) as WinNT.HRESULT
    result.verify("IShellItemArray.GetCount failed")
    return count.value
  }

  fun item(index: Int): NativeShellItem {
    val item = PointerByReference()
    val result = _invokeNativeObject(
      8,
      arrayOf(pointer, index, item),
      WinNT.HRESULT::class.java
    ) as WinNT.HRESULT
    result.verify("IShellItemArray.GetItemAt failed")
    return NativeShellItem(item.value)
  }

  override fun close() {
    Release()
  }
}

@Structure.FieldOrder("pszName", "pszSpec")
internal class ComdlgFilterSpec : Structure() {
  @JvmField
  var pszName: WString? = null

  @JvmField
  var pszSpec: WString? = null

  override fun getFieldOrder(): List<String> = listOf("pszName", "pszSpec")
}

private class FixedClsid(value: String) : Guid.CLSID(value) {
  override fun getFieldOrder(): List<String> = listOf("Data1", "Data2", "Data3", "Data4")
}

private class FixedIid(value: String) : Guid.IID(value) {
  override fun getFieldOrder(): List<String> = listOf("Data1", "Data2", "Data3", "Data4")
}

private val FileOpenDialogClsid = FixedClsid("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
private val FileOpenDialogIid = FixedIid("{D57C7288-D4AD-4768-BE02-9D969532D960}")
private val FileSaveDialogClsid = FixedClsid("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}")
private val FileSaveDialogIid = FixedIid("{84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB}")

private fun WinNT.HRESULT.verify(message: String): WinNT.HRESULT {
  if (COMUtils.FAILED(this)) throw IllegalStateException("$message: 0x${toInt().toUInt().toString(16)}")
  return this
}
