package com.usaginoyami.thumb

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.usaginoyami.theme.UsagiPalettes
import com.usaginoyami.theme.UsagiTheme

class MainActivity : ComponentActivity(), ThumbActions {

    private val vm: ThumbViewModel by viewModels()

    // Seletor de fotos do Android: não precisa de permissão nenhuma.
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleSend(intent)

        setContent {
            var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
            UsagiTheme(palette = UsagiPalettes.all[paletteIndex]) {
                ThumbApp(
                    vm = vm,
                    actions = this@MainActivity,
                    onCyclePalette = { paletteIndex = (paletteIndex + 1) % UsagiPalettes.all.size },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSend(intent)
    }

    // "Compartilhar → Usagi Thumb" a partir de qualquer app de fotos: a imagem entra como camada.
    private fun handleSend(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) vm.addImage(uri)
    }

    override fun pickImage() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun share(uri: Uri, png: Boolean) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (png) "image/png" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Compartilhar miniatura"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum app para compartilhar", Toast.LENGTH_SHORT).show()
        }
    }
}
