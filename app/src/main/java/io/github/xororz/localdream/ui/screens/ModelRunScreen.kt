package io.github.xororz.localdream.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import io.github.xororz.localdream.data.ModelRepository
import io.github.xororz.localdream.data.Model
import io.github.xororz.localdream.data.UpscalerRepository
import io.github.xororz.localdream.data.UpscalerModel
import io.github.xororz.localdream.data.DownloadResult
import io.github.xororz.localdream.service.BackendService
import java.net.URL
import java.net.HttpURLConnection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.Scanner
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.GenerationPreferences
import io.github.xororz.localdream.service.BackgroundGenerationService
import io.github.xororz.localdream.service.BackgroundGenerationService.GenerationState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.geometry.Offset
import io.github.xororz.localdream.BuildConfig

import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.graphics.BitmapFactory
import android.graphics.Rect as AndroidRect
import android.graphics.Canvas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.gestures.detectTransformGestures
import io.github.xororz.localdream.data.DownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

import io.github.xororz.localdream.utils.performUpscale
import io.github.xororz.localdream.utils.saveImage
import io.github.xororz.localdream.utils.reportImage
import io.github.xororz.localdream.data.database.GenerationRepository
import io.github.xororz.localdream.data.ParameterPresets
import io.github.xororz.localdream.data.ParameterPreset
import io.github.xororz.localdream.data.database.RecentPromptsManager
import io.github.xororz.localdream.data.database.RecentPrompt
import io.github.xororz.localdream.data.BatchQueueManager
import io.github.xororz.localdream.data.BatchQueueItem
import io.github.xororz.localdream.data.BatchItemStatus
import io.github.xororz.localdream.ui.dialogs.BatchQueueDialog
import io.github.xororz.localdream.ui.dialogs.AddVariationsDialog


private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        true // Android 10
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private suspend fun checkBackendHealth(
    backendState: StateFlow<BackendService.BackendState>,
    onHealthy: () -> Unit,
    onUnhealthy: () -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)  // 100ms
            .build()

        val startTime = System.currentTimeMillis()
//        val timeoutDuration = 10000
        val timeoutDuration = 60000

        while (currentCoroutineContext().isActive) {
            if (backendState.value is BackendService.BackendState.Error) {
                withContext(Dispatchers.Main) {
                    onUnhealthy()
                }
                break
            }

            if (System.currentTimeMillis() - startTime > timeoutDuration) {
                withContext(Dispatchers.Main) {
                    onUnhealthy()
                }
                break
            }

            try {
                val request = Request.Builder()
                    .url("http://localhost:8081/health")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onHealthy()
                    }
                    break
                }
            } catch (e: Exception) {
                // e
            }

            delay(100)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onUnhealthy()
        }
    }
}

data class GenerationParameters(
    val steps: Int,
    val cfg: Float,
    val seed: Long?,
    val prompt: String,
    val negativePrompt: String,
    val generationTime: String?,
    val size: Int,
    val runOnCpu: Boolean,
    val denoiseStrength: Float = 0.6f,
    val inputImage: String? = null,
    val useOpenCL: Boolean = false
)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelRunScreen(
    modelId: String,
    resolution: Int,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val serviceState by BackgroundGenerationService.generationState.collectAsState()
    val backendState by BackendService.backendState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generationPreferences = remember { GenerationPreferences(context) }
    val generationRepository = remember { GenerationRepository(context) }
    val recentPromptsManager = remember { RecentPromptsManager(context) }
    val recentPrompts by recentPromptsManager.recentPrompts.collectAsState(initial = emptyList())
    val batchQueueManager = remember { BatchQueueManager() }
    val batchQueueState by batchQueueManager.queueState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelRepository = remember { ModelRepository(context) }
    val model = remember { modelRepository.models.find { it.id == modelId } }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showOpenCLWarningDialog by remember { mutableStateOf(false) }

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageVersion by remember { mutableStateOf(0) }
    var generationParams by remember { mutableStateOf<GenerationParameters?>(null) }
    var generationParamsTmp by remember {
        mutableStateOf(
            GenerationParameters(
                steps = 0,
                cfg = 0f,
                seed = 0,
                prompt = "",
                negativePrompt = "",
                generationTime = "",
                size = if (model?.runOnCpu == true) 256 else resolution,
                runOnCpu = model?.runOnCpu ?: false
            )
        )
    }
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var cfg by remember { mutableStateOf(7f) }
    var steps by remember { mutableStateOf(20f) }
    var seed by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(if (model?.runOnCpu == true) 256 else 512) }
    var denoiseStrength by remember { mutableStateOf(0.6f) }
    var useOpenCL by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var base64EncodeDone by remember { mutableStateOf(false) }
    var returnedSeed by remember { mutableStateOf<Long?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBackendReady by remember { mutableStateOf(false) }
    var isCheckingBackend by remember { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showParametersDialog by remember { mutableStateOf(false) }
    var pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var generationTime by remember { mutableStateOf<String?>(null) }
    var generationStartTime by remember { mutableStateOf<Long?>(null) }
    var hasInitialized by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    val isFirstPage by remember { derivedStateOf { pagerState.currentPage == 0 } }
    val isSecondPage by remember { derivedStateOf { pagerState.currentPage == 1 } }

    var isPreviewMode by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val useImg2img = preferences.getBoolean("use_img2img", true)

    var showCropScreen by remember { mutableStateOf(false) }
    var imageUriForCrop by remember { mutableStateOf<Uri?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showInpaintScreen by remember { mutableStateOf(false) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isInpaintMode by remember { mutableStateOf(false) }
    var savedPathHistory by remember { mutableStateOf<List<PathData>?>(null) }
    var cropRect by remember { mutableStateOf<AndroidRect?>(null) }

    var snapshotIsInpaintMode by remember { mutableStateOf(false) }
    var snapshotSelectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var snapshotCropRect by remember { mutableStateOf<AndroidRect?>(null) }

    var saveAllJob: Job? by remember { mutableStateOf(null) }

    // Upscaler related states
    var showUpscalerDialog by remember { mutableStateOf(false) }
    var isUpscaling by remember { mutableStateOf(false) }
    val upscalerRepository = remember { UpscalerRepository(context) }
    val upscalerPreferences =
        remember { context.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE) }

    // Batch queue related states
    var showBatchQueueDialog by remember { mutableStateOf(false) }
    var showAddVariationsDialog by remember { mutableStateOf(false) }

    fun saveAllFields() {
        saveAllJob?.cancel()
        saveAllJob = scope.launch(Dispatchers.IO) {
            delay(1000)
            generationPreferences.saveAllFields(
                modelId = modelId,
                prompt = prompt,
                negativePrompt = negativePrompt,
                steps = steps,
                cfg = cfg,
                seed = seed,
                size = size,
                denoiseStrength = denoiseStrength,
                useOpenCL = useOpenCL
            )
        }
    }

    val onStepsChange = remember { { value: Float -> steps = value; saveAllFields() } }
    val onCfgChange = remember { { value: Float -> cfg = value; saveAllFields() } }
    val onSizeChange = remember {
        { value: Float ->
            val rounded = (value / 64).roundToInt() * 64
            size = rounded.coerceIn(128, 512)
            saveAllFields()
        }
    }
    val onDenoiseStrengthChange =
        remember { { value: Float -> denoiseStrength = value; saveAllFields() } }
    val onSeedChange = remember { { value: String -> seed = value; saveAllFields() } }
    val onPromptChange = remember { { value: String -> prompt = value; saveAllFields() } }
    val onNegativePromptChange =
        remember { { value: String -> negativePrompt = value; saveAllFields() } }

    fun processSelectedImage(uri: Uri) {
        imageUriForCrop = uri
        showCropScreen = true
    }

    fun handleCropComplete(base64String: String, bitmap: Bitmap, rect: AndroidRect) {
        showCropScreen = false
        selectedImageUri = imageUriForCrop
        imageUriForCrop = null
        croppedBitmap = bitmap
        cropRect = rect

        scope.launch(Dispatchers.IO) {
            try {
                base64EncodeDone = false
                val tmpFile = File(context.filesDir, "tmp.txt")
                tmpFile.writeText(base64String)
                base64EncodeDone = true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    selectedImageUri = null
                    croppedBitmap = null
                    cropRect = null
                }
            }
        }
    }

    fun handleInpaintComplete(
        maskBase64: String,
        originalBitmap: Bitmap,
        maskBmp: Bitmap,
        pathHistory: List<PathData>
    ) {
        showInpaintScreen = false
        isInpaintMode = true
        maskBitmap = maskBmp
        savedPathHistory = pathHistory

        scope.launch(Dispatchers.IO) {
            try {
                val tmpFile = File(context.filesDir, "tmp.txt")
                val originalBase64 = tmpFile.readText()

                val maskFile = File(context.filesDir, "mask.txt")
                maskFile.writeText(maskBase64)

                withContext(Dispatchers.Main) {
                    base64EncodeDone = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    isInpaintMode = false
                    maskBitmap = null
                    savedPathHistory = null
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    val contentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    val requestMediaImagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contentPickerLauncher.launch("image/*")
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.media_permission_hint),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contentPickerLauncher.launch("image/*")
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.media_permission_hint),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onSelectImageClick() {
        when {
            // Android 13+
            Build.VERSION.SDK_INT >= 33 -> {
                // PhotoPicker API
                photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            }

            // Android 12-
            else -> {
                when {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        contentPickerLauncher.launch("image/*")
                    }

                    else -> {
                        requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }
        }
    }

    fun handleSaveImage(
        context: Context,
        bitmap: Bitmap,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!checkStoragePermission(context)) {
            onError("need storage permission to save image")
            return
        }

        coroutineScope.launch {
            if (snapshotIsInpaintMode && snapshotCropRect != null && snapshotSelectedImageUri != null) {
                withContext(Dispatchers.IO) {
                    var originalBitmap: Bitmap? = null
                    var mutableOriginal: Bitmap? = null
                    var resizedPatch: Bitmap? = null
                    try {
                        originalBitmap =
                            context.contentResolver.openInputStream(snapshotSelectedImageUri!!)!!
                                .use {
                                    BitmapFactory.decodeStream(it)
                                }

                        mutableOriginal = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

                        val patch = bitmap
                        resizedPatch = Bitmap.createScaledBitmap(
                            patch,
                            snapshotCropRect!!.width(),
                            snapshotCropRect!!.height(),
                            true
                        )

                        val canvas = Canvas(mutableOriginal)
                        canvas.drawBitmap(
                            resizedPatch,
                            snapshotCropRect!!.left.toFloat(),
                            snapshotCropRect!!.top.toFloat(),
                            null
                        )

                        saveImage(
                            context = context,
                            bitmap = mutableOriginal,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Failed to create composite image: ${e.localizedMessage}")
                        }
                    }
                }
            } else {
                saveImage(
                    context = context,
                    bitmap = bitmap,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        }
    }

    fun cleanup() {
        try {
            currentBitmap = null
            generationParams = null
            context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
            val backendServiceIntent = Intent(context, BackendService::class.java)
            context.stopService(backendServiceIntent)
            isRunning = false
            progress = 0f
            errorMessage = null
            BackgroundGenerationService.resetState()
            coroutineScope.launch {
                pagerState.scrollToPage(0)
            }
            saveAllJob?.cancel()
        } catch (e: Exception) {
            android.util.Log.e("ModelRunScreen", "error", e)
        }
    }

    fun handleExit() {
        cleanup()
        BackgroundGenerationService.clearCompleteState()
        navController.navigateUp()
    }

    LaunchedEffect(modelId) {
        if (!hasInitialized) {
            val prefs = generationPreferences.getPreferences(modelId).first()

            if (prefs.prompt.isEmpty() && prefs.negativePrompt.isEmpty()) {
                model?.let { m ->
                    if (m.defaultPrompt.isNotEmpty()) {
                        generationPreferences.savePrompt(modelId, m.defaultPrompt)
                        prompt = m.defaultPrompt
                    }
                    if (m.defaultNegativePrompt.isNotEmpty()) {
                        generationPreferences.saveNegativePrompt(
                            modelId,
                            m.defaultNegativePrompt
                        )
                        negativePrompt = m.defaultNegativePrompt
                    }
                }
            } else {
                prompt = prefs.prompt
                negativePrompt = prefs.negativePrompt
            }

            steps = prefs.steps
            cfg = prefs.cfg
            seed = prefs.seed
            size = if (model?.runOnCpu == true) prefs.size else resolution
            denoiseStrength = prefs.denoiseStrength
            useOpenCL = prefs.useOpenCL

            hasInitialized = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            cleanup()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (backendState !is BackendService.BackendState.Running) {
                        val intent = Intent(context, BackendService::class.java).apply {
                            putExtra("modelId", model?.id)
                            putExtra("resolution", resolution)
                            putExtra("use_opencl", useOpenCL)
                        }
                        context.startForegroundService(intent)
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    cleanup()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cleanup()
        }
    }

    LaunchedEffect(serviceState) {
        when (val state = serviceState) {
            is GenerationState.Progress -> {
                if (progress == 0f) {
                    generationStartTime = System.currentTimeMillis()
                }
                progress = state.progress
                isRunning = true
            }

            is GenerationState.Complete -> {
                withContext(Dispatchers.Main) {
                    android.util.Log.d("ModelRunScreen", "update bitmap")

                    currentBitmap = state.bitmap
                    imageVersion += 1

                    snapshotIsInpaintMode = isInpaintMode
                    snapshotSelectedImageUri = selectedImageUri
                    snapshotCropRect = cropRect

                    state.seed?.let { returnedSeed = it }
                    isRunning = false
                    progress = 0f

                    val genTime = generationStartTime?.let { startTime ->
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        when {
                            duration < 1000 -> "${duration}ms"
                            duration < 60000 -> String.format("%.1fs", duration / 1000.0)
                            else -> String.format(
                                "%dm%ds",
                                duration / 60000,
                                (duration % 60000) / 1000
                            )
                        }
                    }

                    generationParams = GenerationParameters(
                        steps = generationParamsTmp.steps,
                        cfg = generationParamsTmp.cfg,
                        seed = returnedSeed,
                        prompt = generationParamsTmp.prompt,
                        negativePrompt = generationParamsTmp.negativePrompt,
                        generationTime = genTime,
                        size = if (model?.runOnCpu == true) generationParamsTmp.size else resolution
                            ?: 512,
                        runOnCpu = model?.runOnCpu ?: false,
                        useOpenCL = generationParamsTmp.useOpenCL
                    )

                    android.util.Log.d(
                        "ModelRunScreen",
                        "params update: ${generationParams?.steps}, ${generationParams?.cfg}"
                    )

                    generationTime = genTime
                    generationStartTime = null

                    // Save to history database
                    scope.launch(Dispatchers.IO) {
                        try {
                            generationRepository.saveGeneration(
                                bitmap = state.bitmap,
                                prompt = generationParamsTmp.prompt,
                                negativePrompt = generationParamsTmp.negativePrompt,
                                modelId = modelId,
                                modelName = model?.name ?: "Unknown",
                                steps = generationParamsTmp.steps,
                                cfg = generationParamsTmp.cfg,
                                seed = returnedSeed ?: 0L,
                                width = if (model?.runOnCpu == true) generationParamsTmp.size else resolution ?: 512,
                                height = if (model?.runOnCpu == true) generationParamsTmp.size else resolution ?: 512,
                                runtime = if (generationParamsTmp.runOnCpu) {
                                    if (generationParamsTmp.useOpenCL) "GPU" else "CPU"
                                } else "NPU",
                                generationTime = genTime,
                                denoiseStrength = generationParamsTmp.denoiseStrength,
                                inputImagePath = generationParamsTmp.inputImage,
                                isInpaintMode = isInpaintMode
                            )
                            android.util.Log.d("ModelRunScreen", "Saved to history database")
                        } catch (e: Exception) {
                            android.util.Log.e("ModelRunScreen", "Failed to save to history", e)
                        }
                    }

                    if (pagerState.currentPage != 1) {
                        pagerState.animateScrollToPage(1)
                    }
                }
            }

            is GenerationState.Error -> {
                errorMessage = state.message
                isRunning = false
                progress = 0f
            }

            else -> {
                isRunning = false
                progress = 0f
            }
        }
    }

    BackHandler {
        if (isRunning) {
            showExitDialog = true
        } else {
            handleExit()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.confirm_exit)) },
            text = { Text(stringResource(R.string.confirm_exit_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        handleExit()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showOpenCLWarningDialog) {
        AlertDialog(
            onDismissRequest = { showOpenCLWarningDialog = false },
            title = { Text("GPU Runtime Warning") },
            text = { Text(stringResource(R.string.opencl_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOpenCLWarningDialog = false
                        useOpenCL = true
                        saveAllFields()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenCLWarningDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.reset)) },
            text = { Text(stringResource(R.string.reset_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        steps = 20f
                        cfg = 7f
                        seed = ""
                        prompt = model?.defaultPrompt ?: ""
                        negativePrompt = model?.defaultNegativePrompt ?: ""
                        denoiseStrength = 0.6f

                        scope.launch(Dispatchers.IO) {
                            generationPreferences.saveAllFields(
                                modelId = modelId,
                                prompt = model?.defaultPrompt ?: "",
                                negativePrompt = model?.defaultNegativePrompt ?: "",
                                steps = 20f,
                                cfg = 7f,
                                seed = "",
                                size = 256,
                                denoiseStrength = 0.6f,
                                useOpenCL = useOpenCL
                            )
                        }
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Batch queue dialog
    if (showBatchQueueDialog) {
        BatchQueueDialog(
            queueState = batchQueueState,
            onDismiss = { showBatchQueueDialog = false },
            onRemoveItem = { itemId ->
                batchQueueManager.removeItem(itemId)
            },
            onClearQueue = {
                batchQueueManager.clearQueue()
            },
            onStartBatch = {
                batchQueueManager.setProcessing(true)
                scope.launch {
                    processBatchQueue(
                        context = context,
                        batchQueueManager = batchQueueManager,
                        modelId = modelId,
                        resolution = resolution,
                        useOpenCL = useOpenCL,
                        onComplete = {
                            batchQueueManager.setProcessing(false)
                        }
                    )
                }
            },
            onStopBatch = {
                batchQueueManager.setProcessing(false)
            }
        )
    }
    
    // Add variations dialog
    if (showAddVariationsDialog) {
        AddVariationsDialog(
            onDismiss = { showAddVariationsDialog = false },
            onConfirm = { count ->
                val items = (1..count).map {
                    BatchQueueItem(
                        prompt = prompt,
                        negativePrompt = negativePrompt,
                        steps = steps.toInt(),
                        cfg = cfg,
                        seed = System.currentTimeMillis() + it,
                        width = size,
                        height = size,
                        denoiseStrength = if (selectedImageUri != null) denoiseStrength else null
                    )
                }
                batchQueueManager.addItems(items)
                Toast.makeText(
                    context,
                    context.getString(R.string.added_to_batch),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    LaunchedEffect(Unit) {
        checkBackendHealth(
            backendState = BackendService.backendState,
            onHealthy = {
                isBackendReady = true
                isCheckingBackend = false
            },
            onUnhealthy = {
                isBackendReady = false
                isCheckingBackend = false
                errorMessage = context.getString(R.string.backend_failed)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(model?.name ?: "Running Model")
                            Text(
                                model?.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isRunning) {
                                showExitDialog = true
                            } else {
                                handleExit()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        // Batch queue button with badge
                        BadgedBox(
                            badge = {
                                if (batchQueueState.items.isNotEmpty()) {
                                    Badge {
                                        Text("${batchQueueState.items.size}")
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = { showBatchQueueDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.QueuePlayNext,
                                    contentDescription = stringResource(R.string.batch_queue)
                                )
                            }
                        }
                        
                        Row {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        focusManager.clearFocus()
                                        pagerState.animateScrollToPage(0)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isFirstPage)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            ) {
                                Text(stringResource(R.string.prompt_tab))
                            }
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        focusManager.clearFocus()
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isSecondPage)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            ) {
                                Text(stringResource(R.string.result_tab))
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (model != null) {

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) { page ->
                    when (page) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                stringResource(R.string.prompt_settings),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            var showAdvancedSettings by remember {
                                                mutableStateOf(
                                                    false
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (useImg2img) {
                                                    TextButton(
                                                        onClick = {
                                                            onSelectImageClick()
                                                        }
                                                    ) {
                                                        Text(
                                                            "img2img",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.padding(end = 4.dp)
                                                        )
                                                        Icon(
                                                            Icons.Default.Image,
                                                            contentDescription = "select image",
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                TextButton(
                                                    onClick = { showAdvancedSettings = true }
                                                ) {
                                                    Text(
                                                        stringResource(R.string.advanced_settings),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                    Icon(
                                                        Icons.Default.Settings,
                                                        contentDescription = stringResource(R.string.settings),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            if (showAdvancedSettings) {
                                                AlertDialog(
                                                    onDismissRequest = {
                                                        showAdvancedSettings = false
                                                    },
                                                    title = { Text(stringResource(R.string.advanced_settings_title)) },
                                                    text = {
                                                        Column(
                                                            verticalArrangement = Arrangement.spacedBy(
                                                                8.dp
                                                            ),
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        ) {
                                                            Column {
                                                                Text(
                                                                    stringResource(
                                                                        R.string.steps,
                                                                        steps.roundToInt()
                                                                    ),
                                                                    style = MaterialTheme.typography.bodyMedium
                                                                )
                                                                Slider(
                                                                    value = steps,
                                                                    onValueChange = onStepsChange,
                                                                    valueRange = 1f..50f,
                                                                    steps = 48,
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }

                                                            Column {
                                                                Text(
                                                                    "CFG Scale: %.1f".format(cfg),
                                                                    style = MaterialTheme.typography.bodyMedium
                                                                )
                                                                Slider(
                                                                    value = cfg,
                                                                    onValueChange = onCfgChange,
                                                                    valueRange = 1f..30f,
                                                                    steps = 57,
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }
                                                            if (model.runOnCpu) {
                                                                Column {
                                                                    Text(
                                                                        stringResource(
                                                                            R.string.image_size,
                                                                            size,
                                                                            size
                                                                        ),
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                    )
                                                                    Slider(
                                                                        value = size.toFloat(),
                                                                        onValueChange = onSizeChange,
                                                                        valueRange = 128f..512f,
                                                                        steps = 5,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    )
                                                                }
                                                            }
                                                            if (model.runOnCpu) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(
                                                                        8.dp
                                                                    )
                                                                ) {
                                                                    Text(
                                                                        "Runtime",
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                    )
                                                                    FilterChip(
                                                                        selected = !useOpenCL,
                                                                        onClick = {
                                                                            useOpenCL = false
                                                                            saveAllFields()
                                                                        },
                                                                        label = { Text("CPU") },
                                                                        modifier = Modifier.weight(
                                                                            1f
                                                                        )
                                                                    )
                                                                    FilterChip(
                                                                        selected = useOpenCL,
                                                                        onClick = {
                                                                            if (!useOpenCL) {
                                                                                showOpenCLWarningDialog =
                                                                                    true
                                                                            } else {
                                                                                useOpenCL = false
                                                                                saveAllFields()
                                                                            }
                                                                        },
                                                                        label = { Text("GPU") },
                                                                        modifier = Modifier.weight(
                                                                            1f
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                            if (useImg2img) {
                                                                Column {
                                                                    Text(
                                                                        "[img2img]Denoise Strength: %.2f".format(
                                                                            denoiseStrength
                                                                        ),
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                    )
                                                                    Slider(
                                                                        value = denoiseStrength,
                                                                        onValueChange = onDenoiseStrengthChange,
                                                                        valueRange = 0f..1f,
                                                                        steps = 99,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    )
                                                                }
                                                            }
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalArrangement = Arrangement.spacedBy(
                                                                    8.dp
                                                                )
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = seed,
                                                                    onValueChange = onSeedChange,
                                                                    label = { Text(stringResource(R.string.random_seed)) },
                                                                    keyboardOptions = KeyboardOptions(
                                                                        keyboardType = KeyboardType.Number
                                                                    ),
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = MaterialTheme.shapes.medium,
                                                                    trailingIcon = {
                                                                        if (seed.isNotEmpty()) {
                                                                            IconButton(onClick = {
                                                                                seed = ""
                                                                                saveAllFields()
                                                                            }) {
                                                                                Icon(
                                                                                    Icons.Default.Clear,
                                                                                    contentDescription = "clear"
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                )

                                                                if (returnedSeed != null) {
                                                                    FilledTonalButton(
                                                                        onClick = {
                                                                            seed =
                                                                                returnedSeed.toString()
                                                                            saveAllFields()
                                                                        },
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Refresh,
                                                                            contentDescription = stringResource(
                                                                                R.string.use_last_seed
                                                                            ),
                                                                            modifier = Modifier
                                                                                .size(
                                                                                    20.dp
                                                                                )
                                                                                .padding(end = 4.dp)
                                                                        )
                                                                        Text(
                                                                            stringResource(
                                                                                R.string.use_last_seed,
                                                                                returnedSeed.toString()
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    confirmButton = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    showResetConfirmDialog = true
                                                                },
                                                                colors = ButtonDefaults.textButtonColors(
                                                                    contentColor = MaterialTheme.colorScheme.error
                                                                )
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Refresh,
                                                                    contentDescription = stringResource(
                                                                        R.string.reset
                                                                    ),
                                                                    modifier = Modifier
                                                                        .size(20.dp)
                                                                        .padding(end = 4.dp)
                                                                )
                                                                Text(stringResource(R.string.reset))
                                                            }

                                                            TextButton(onClick = {
                                                                showAdvancedSettings = false
                                                            }) {
                                                                Text(stringResource(R.string.confirm))
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        // Quality Presets
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.quality_presets),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ParameterPresets.ALL_PRESETS.forEach { preset ->
                                                    val isSelected = steps.roundToInt() == preset.steps && 
                                                                    kotlin.math.abs(cfg - preset.cfg) < 0.1f
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            steps = preset.steps.toFloat()
                                                            cfg = preset.cfg
                                                            saveAllFields()
                                                        },
                                                        label = { 
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Text(
                                                                    preset.icon,
                                                                    style = MaterialTheme.typography.titleMedium
                                                                )
                                                                Text(
                                                                    preset.name,
                                                                    style = MaterialTheme.typography.labelSmall
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            Text(
                                                stringResource(R.string.quality_presets_hint),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }

                                        // Recent Prompts Button
                                        if (recentPrompts.isNotEmpty()) {
                                            var showRecentPrompts by remember { mutableStateOf(false) }
                                            
                                            OutlinedButton(
                                                onClick = { showRecentPrompts = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Icons.Default.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.recent_prompts, recentPrompts.size))
                                            }
                                            
                                            if (showRecentPrompts) {
                                                RecentPromptsDialog(
                                                    recentPrompts = recentPrompts,
                                                    onDismiss = { showRecentPrompts = false },
                                                    onSelectPrompt = { recentPrompt ->
                                                        prompt = recentPrompt.prompt
                                                        negativePrompt = recentPrompt.negativePrompt
                                                        saveAllFields()
                                                        showRecentPrompts = false
                                                    },
                                                    onDeletePrompt = { recentPrompt ->
                                                        scope.launch {
                                                            recentPromptsManager.removePrompt(recentPrompt)
                                                        }
                                                    },
                                                    onClearAll = {
                                                        scope.launch {
                                                            recentPromptsManager.clearAll()
                                                        }
                                                        showRecentPrompts = false
                                                    }
                                                )
                                            }
                                        }

                                        var expandedPrompt by remember { mutableStateOf(false) }
                                        var expandedNegativePrompt by remember {
                                            mutableStateOf(
                                                false
                                            )
                                        }

                                        OutlinedTextField(
                                            value = prompt,
                                            onValueChange = onPromptChange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) { },
                                            label = { Text(stringResource(R.string.image_prompt)) },
                                            maxLines = if (expandedPrompt) Int.MAX_VALUE else 2,
                                            minLines = if (expandedPrompt) 3 else 2,
                                            shape = MaterialTheme.shapes.medium,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                            ),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    expandedPrompt = !expandedPrompt
                                                }) {
                                                    Icon(
                                                        if (expandedPrompt) Icons.Default.KeyboardArrowUp
                                                        else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = if (expandedPrompt) "collapse" else "expand"
                                                    )
                                                }
                                            }
                                        )

                                        OutlinedTextField(
                                            value = negativePrompt,
                                            onValueChange = onNegativePromptChange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) { },
                                            label = { Text(stringResource(R.string.negative_prompt)) },
                                            maxLines = if (expandedNegativePrompt) Int.MAX_VALUE else 2,
                                            minLines = if (expandedNegativePrompt) 3 else 2,
                                            shape = MaterialTheme.shapes.medium,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                            ),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    expandedNegativePrompt = !expandedNegativePrompt
                                                }) {
                                                    Icon(
                                                        if (expandedNegativePrompt) Icons.Default.KeyboardArrowUp
                                                        else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = if (expandedNegativePrompt) "collapse" else "expand"
                                                    )
                                                }
                                            }
                                        )

                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                android.util.Log.d(
                                                    "ModelRunScreen",
                                                    "start generation"
                                                )
                                                generationParamsTmp = GenerationParameters(
                                                    steps = steps.roundToInt(),
                                                    cfg = cfg,
                                                    seed = 0,
                                                    prompt = prompt,
                                                    negativePrompt = negativePrompt,
                                                    generationTime = "",
                                                    size = size,
                                                    runOnCpu = model.runOnCpu,
                                                    denoiseStrength = denoiseStrength,
                                                    inputImage = null,
                                                    useOpenCL = useOpenCL
                                                )

                                                val intent = Intent(
                                                    context,
                                                    BackgroundGenerationService::class.java
                                                ).apply {
                                                    putExtra("prompt", prompt)
                                                    putExtra("negative_prompt", negativePrompt)
                                                    putExtra("steps", steps.roundToInt())
                                                    putExtra("cfg", cfg)
                                                    seed.toLongOrNull()
                                                        ?.let { putExtra("seed", it) }
                                                    putExtra("size", size)
                                                    putExtra("denoise_strength", denoiseStrength)
                                                    putExtra("use_opencl", useOpenCL)

                                                    if (selectedImageUri != null && base64EncodeDone) {
                                                        putExtra("has_image", true)
                                                        if (isInpaintMode && maskBitmap != null) {
                                                            putExtra("has_mask", true)
                                                        }
                                                    }
                                                }
                                                android.util.Log.d(
                                                    "ModelRunScreen",
                                                    "start service"
                                                )
                                                
                                                // Save to recent prompts
                                                scope.launch {
                                                    recentPromptsManager.addPrompt(prompt, negativePrompt)
                                                }
                                                
                                                context.startForegroundService(intent)
                                                android.util.Log.d(
                                                    "ModelRunScreen",
                                                    "start service sent"
                                                )
                                            },
                                            enabled = serviceState !is GenerationState.Progress && !isUpscaling,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            if (serviceState is GenerationState.Progress || isUpscaling) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else {
                                                Text(stringResource(R.string.generate_image))
                                            }
                                        }
                                        
                                        // Batch generation buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val item = BatchQueueItem(
                                                        prompt = prompt,
                                                        negativePrompt = negativePrompt,
                                                        steps = steps.toInt(),
                                                        cfg = cfg,
                                                        seed = seed.toLongOrNull() ?: System.currentTimeMillis(),
                                                        width = size,
                                                        height = size,
                                                        denoiseStrength = if (selectedImageUri != null) denoiseStrength else null
                                                    )
                                                    batchQueueManager.addItem(item)
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.added_to_batch),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                enabled = serviceState !is GenerationState.Progress && !isUpscaling,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    Icons.Default.PlaylistAdd,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.add_to_batch))
                                            }
                                            
                                            OutlinedButton(
                                                onClick = { showAddVariationsDialog = true },
                                                enabled = serviceState !is GenerationState.Progress && !isUpscaling,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.add_variations))
                                            }
                                        }
                                    }
                                }


                                AnimatedVisibility(
                                    visible = errorMessage != null,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    errorMessage?.let { msg ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Error,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    msg,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = isRunning,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.generating),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "${(progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = selectedImageUri != null && base64EncodeDone,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .size(100.dp),
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Box {
                                                    croppedBitmap?.let { bitmap ->
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(bitmap)
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = "Cropped Image",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } ?: selectedImageUri?.let { uri ->
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(
                                                                LocalContext.current
                                                            )
                                                                .data(uri)
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = "Selected Image",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            selectedImageUri = null
                                                            croppedBitmap = null
                                                            maskBitmap = null
                                                            isInpaintMode = false
                                                            cropRect = null
                                                            savedPathHistory = null
                                                        },
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(
                                                                color = MaterialTheme.colorScheme.surface.copy(
                                                                    alpha = 0.7f
                                                                ),
                                                                shape = CircleShape
                                                            )
                                                            .align(Alignment.TopEnd)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Remove Image",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            AnimatedVisibility(
                                                visible = croppedBitmap != null && !isInpaintMode,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally()
                                            ) {
                                                Row {
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            if (croppedBitmap != null) {
                                                                showInpaintScreen = true
                                                            } else {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Please Crop First",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        },
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Brush,
                                                            contentDescription = "Set Mask",
                                                        )
                                                    }
                                                }
                                            }

                                            AnimatedVisibility(
                                                visible = isInpaintMode && maskBitmap != null,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally()
                                            ) {
                                                Row {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Card(
                                                        modifier = Modifier
                                                            .size(100.dp)
                                                            .clickable {
                                                                if (croppedBitmap != null && maskBitmap != null) {
                                                                    showInpaintScreen = true
                                                                }
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Box {
                                                            maskBitmap?.let { mb ->
                                                                AsyncImage(
                                                                    model = ImageRequest.Builder(LocalContext.current)
                                                                        .data(mb)
                                                                        .crossfade(true)
                                                                        .build(),
                                                                    contentDescription = "Mask Image",
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    maskBitmap = null
                                                                    isInpaintMode = false
                                                                    savedPathHistory = null
                                                                },
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .background(
                                                                        color = MaterialTheme.colorScheme.surface.copy(
                                                                            alpha = 0.7f
                                                                        ),
                                                                        shape = CircleShape
                                                                    )
                                                                    .align(Alignment.TopEnd)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Clear,
                                                                    contentDescription = "Clear Mask",
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = currentBitmap == null,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                stringResource(R.string.no_results),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                stringResource(R.string.no_results_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(0)
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 8.dp)
                                            ) {
                                                Text(stringResource(R.string.go_to_generate))
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = currentBitmap != null,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    stringResource(R.string.result_tab),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                currentBitmap?.let { bitmap ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                    ) {
                                                        if (BuildConfig.FLAVOR == "filter") {
                                                            FilledTonalIconButton(
                                                                onClick = {
                                                                    showReportDialog = true
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Report,
                                                                    contentDescription = "report inappropriate content"
                                                                )
                                                            }
                                                        }

                                                        // Upscaler button - only show for NPU runtime and resolution <= 1024
                                                        if (model?.runOnCpu == false &&
                                                            generationParams?.size?.let { it <= 1024 } == true
                                                        ) {
                                                            FilledTonalIconButton(
                                                                onClick = {
                                                                    showUpscalerDialog = true
                                                                },
                                                                enabled = !isRunning && !isUpscaling
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.AutoFixHigh,
                                                                    contentDescription = "upscale image"
                                                                )
                                                            }
                                                        }

                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                handleSaveImage(
                                                                    context = context,
                                                                    bitmap = bitmap,
                                                                    onSuccess = {
                                                                        Toast.makeText(
                                                                            context,
                                                                            context.getString(R.string.image_saved),
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    },
                                                                    onError = { error ->
                                                                        Toast.makeText(
                                                                            context,
                                                                            error,
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }
                                                                )
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Save,
                                                                contentDescription = "save image"
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            key(imageVersion) {
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                        .clickable {
                                                            if (currentBitmap != null) {
                                                                isPreviewMode = true
                                                                scale = 1f
                                                                offsetX = 0f
                                                                offsetY = 0f
                                                            }
                                                        },
                                                    shape = MaterialTheme.shapes.medium,
                                                    shadowElevation = 4.dp
                                                ) {
                                                    currentBitmap?.let { bitmap ->
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(bitmap)
                                                                .size(coil.size.Size.ORIGINAL)
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = "generated image",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showParametersDialog = true },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            stringResource(R.string.generation_params),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Icon(
                                                            Icons.Default.Info,
                                                            contentDescription = "view details",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    generationParams?.let { params ->
                                                        Text(
                                                            stringResource(
                                                                R.string.result_params,
                                                                params.steps,
                                                                params.cfg,
                                                                params.seed.toString()
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.8f
                                                            )
                                                        )
                                                        Text(
                                                            stringResource(
                                                                R.string.result_params_2,
                                                                params.size,
                                                                params.generationTime ?: "unknown",
                                                                if (params.runOnCpu) {
                                                                    if (params.useOpenCL) "GPU" else "CPU"
                                                                } else "NPU"
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.8f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (showReportDialog && currentBitmap != null && generationParams != null) {
                                    AlertDialog(
                                        onDismissRequest = { showReportDialog = false },
                                        title = { Text("Report") },
                                        text = {
                                            Column {
//                                                Text("Report this image?")
                                                Text(
                                                    "Report this image if you feel it is inappropriate. Params and image will be sent to the server for review.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    showReportDialog = false
                                                    coroutineScope.launch {
                                                        currentBitmap?.let { bitmap ->
                                                            reportImage(
                                                                context = context,
                                                                bitmap = bitmap,
                                                                modelName = model.name,
                                                                params = generationParams!!,
                                                                onSuccess = {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Thanks for your report.",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                },
                                                                onError = { error ->
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Error: $error",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            )
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Report")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showReportDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }
                                if (showParametersDialog && generationParams != null) {
                                    AlertDialog(
                                        onDismissRequest = { showParametersDialog = false },
                                        title = { Text(stringResource(R.string.params_detail)) },
                                        text = {
                                            Column(
                                                modifier = Modifier
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        stringResource(R.string.basic_params),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_step,
                                                            generationParams?.steps ?: 0
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        "CFG: %.1f".format(generationParams?.cfg),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_size,
                                                            generationParams?.size ?: 0,
                                                            generationParams?.size ?: 0
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    generationParams?.seed?.let {
                                                        Text(
                                                            stringResource(R.string.basic_seed, it),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_runtime,
                                                            if (generationParams?.runOnCpu == true) {
                                                                if (generationParams?.useOpenCL == true) "GPU" else "CPU"
                                                            } else "NPU"
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_time,
                                                            generationParams?.generationTime
                                                                ?: "unknown"
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        stringResource(R.string.image_prompt),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        generationParams?.prompt ?: "",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        stringResource(R.string.negative_prompt),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        generationParams?.negativePrompt ?: "",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showParametersDialog = false }) {
                                                Text(stringResource(R.string.close))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showCropScreen && imageUriForCrop != null) {
            CropImageScreen(
                imageUri = imageUriForCrop!!,
                onCropComplete = { base64String, bitmap, rect ->
                    handleCropComplete(base64String, bitmap, rect)
                },
                onCancel = {
                    showCropScreen = false
                    imageUriForCrop = null
                    selectedImageUri = null
                }
            )
        }
        if (showInpaintScreen && croppedBitmap != null) {
            InpaintScreen(
                originalBitmap = croppedBitmap!!,
                existingMaskBitmap = if (isInpaintMode) maskBitmap else null,
                existingPathHistory = savedPathHistory,
                onInpaintComplete = { maskBase64, originalBitmap, maskBitmap, pathHistory ->
                    handleInpaintComplete(maskBase64, originalBitmap, maskBitmap, pathHistory)
                },
                onCancel = {
                    showInpaintScreen = false
                }
            )
        }
    }
    if (isPreviewMode && currentBitmap != null) {
        BackHandler {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            isPreviewMode = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        scale = (scale * zoom).coerceIn(0.5f, 5f)

                        val centerX = this.size.width / 2f
                        val centerY = this.size.height / 2f

                        val focusX = (centroid.x - centerX - offsetX) / oldScale
                        val focusY = (centroid.y - centerY - offsetY) / oldScale

                        offsetX += focusX * oldScale - focusX * scale
                        offsetY += focusY * oldScale - focusY * scale

                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val centerX = this.size.width / 2f
                            val centerY = this.size.height / 2f
                            val imageSize = minOf(this.size.width, this.size.height).toFloat()
                            val scaledImageSize = imageSize * scale

                            val left = centerX - scaledImageSize / 2f + offsetX
                            val top = centerY - scaledImageSize / 2f + offsetY
                            val right = left + scaledImageSize
                            val bottom = top + scaledImageSize

                            if (offset.x < left || offset.x > right ||
                                offset.y < top || offset.y > bottom
                            ) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                isPreviewMode = false
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentBitmap!!)
                    .size(coil.size.Size.ORIGINAL)
                    .crossfade(true)
                    .build(),
                contentDescription = "preview image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        isPreviewMode = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "close preview",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "reset zoom",
                    tint = Color.White
                )
            }

            Text(
                text = "${(scale * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    // Upscaler dialog
    if (showUpscalerDialog) {
        var tempSelectedUpscalerId by remember {
            mutableStateOf(upscalerPreferences.getString("${modelId}_selected_upscaler", null))
        }
        var downloadingUpscalerId by remember { mutableStateOf<String?>(null) }
        var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }

        UpscalerSelectDialog(
            upscalers = upscalerRepository.upscalers,
            selectedUpscalerId = tempSelectedUpscalerId,
            downloadingUpscalerId = downloadingUpscalerId,
            downloadProgress = downloadProgress,
            onDismiss = { showUpscalerDialog = false },
            onSelectUpscaler = { upscalerId ->
                tempSelectedUpscalerId = upscalerId
            },
            onConfirm = {
                val selectedUpscaler =
                    upscalerRepository.upscalers.find { it.id == tempSelectedUpscalerId }
                if (selectedUpscaler != null && selectedUpscaler.isDownloaded) {
                    // Save selection
                    upscalerPreferences.edit()
                        .putString("${modelId}_selected_upscaler", selectedUpscaler.id).apply()
                    showUpscalerDialog = false

                    // Execute upscale
                    currentBitmap?.let { bitmap ->
                        isUpscaling = true
                        scope.launch {
                            try {
                                val upscaledBitmap = performUpscale(
                                    context = context,
                                    bitmap = bitmap,
                                    modelId = modelId,
                                    upscalerId = selectedUpscaler.id
                                )
                                currentBitmap = upscaledBitmap
                                imageVersion++
                                generationParams = generationParams?.copy(
                                    size = upscaledBitmap.width
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.upscale_failed,
                                        e.message ?: "Unknown error"
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isUpscaling = false
                            }
                        }
                    }
                } else if (selectedUpscaler != null && !selectedUpscaler.isDownloaded) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.download_model_first),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDownload = { upscaler ->
                downloadingUpscalerId = upscaler.id
                downloadProgress = null
                scope.launch {
                    upscalerRepository.downloadUpscaler(upscaler).collect { result ->
                        when (result) {
                            is DownloadResult.Progress -> {
                                downloadProgress = result.progress
                            }

                            is DownloadResult.Success -> {
                                downloadingUpscalerId = null
                                downloadProgress = null
                                upscalerRepository.refreshUpscalerState(upscaler.id)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.download_done),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            is DownloadResult.Error -> {
                                downloadingUpscalerId = null
                                downloadProgress = null
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.error_download_failed,
                                        result.message
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        )
    }

    AnimatedVisibility(
        visible = isCheckingBackend,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.loading_model),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // Upscaling overlay
    if (isUpscaling) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.upscaling_image),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun UpscalerSelectDialog(
    upscalers: List<UpscalerModel>,
    selectedUpscalerId: String?,
    downloadingUpscalerId: String?,
    downloadProgress: DownloadProgress?,
    onDismiss: () -> Unit,
    onSelectUpscaler: (String) -> Unit,
    onConfirm: () -> Unit,
    onDownload: (UpscalerModel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_upscaler_model)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(upscalers) { upscaler ->
                    UpscalerModelCard(
                        upscaler = upscaler,
                        isSelected = upscaler.id == selectedUpscalerId,
                        isDownloading = upscaler.id == downloadingUpscalerId,
                        downloadProgress = if (upscaler.id == downloadingUpscalerId) downloadProgress else null,
                        onSelect = { onSelectUpscaler(upscaler.id) },
                        onDownload = { onDownload(upscaler) }
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = selectedUpscalerId != null
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    )
}

@Composable
fun UpscalerModelCard(
    upscaler: UpscalerModel,
    isSelected: Boolean,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = upscaler.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = upscaler.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (!upscaler.isDownloaded) {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.download),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.download))
                    }
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Show progress bar when downloading
            if (isDownloading && downloadProgress != null) {
                LinearProgressIndicator(
                    progress = downloadProgress.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun RecentPromptsDialog(
    recentPrompts: List<RecentPrompt>,
    onDismiss: () -> Unit,
    onSelectPrompt: (RecentPrompt) -> Unit,
    onDeletePrompt: (RecentPrompt) -> Unit,
    onClearAll: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.recent_prompts_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        if (recentPrompts.isNotEmpty()) {
                            TextButton(onClick = { showClearConfirm = true }) {
                                Text(stringResource(R.string.clear_all))
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
                Divider()
                
                // List
                if (recentPrompts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Text(
                                stringResource(R.string.no_recent_prompts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentPrompts.size) { index ->
                            val recentPrompt = recentPrompts[index]
                            
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectPrompt(recentPrompt) }
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                dateFormat.format(Date(recentPrompt.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Text(
                                            recentPrompt.prompt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        if (recentPrompt.negativePrompt.isNotBlank()) {
                                            Text(
                                                "Negative: ${recentPrompt.negativePrompt}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = { onDeletePrompt(recentPrompt) },
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_all_prompts)) },
            text = { Text(stringResource(R.string.clear_all_prompts_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// Batch queue processing function
private suspend fun processBatchQueue(
    context: Context,
    batchQueueManager: BatchQueueManager,
    modelId: String,
    resolution: Int,
    useOpenCL: Boolean,
    onComplete: () -> Unit
) {
    while (batchQueueManager.queueState.value.isProcessing) {
        val nextItem = batchQueueManager.getNextPendingItem()
        
        if (nextItem == null) {
            // No more pending items, finish
            onComplete()
            break
        }
        
        // Mark as processing
        batchQueueManager.updateItemStatus(nextItem.id, BatchItemStatus.PROCESSING)
        
        try {
            // Start the generation service
            val intent = Intent(context, BackgroundGenerationService::class.java).apply {
                action = BackgroundGenerationService.ACTION_START_GENERATION
                putExtra("model_id", modelId)
                putExtra("resolution", resolution)
                putExtra("prompt", nextItem.prompt)
                putExtra("negative_prompt", nextItem.negativePrompt)
                putExtra("steps", nextItem.steps)
                putExtra("cfg", nextItem.cfg)
                putExtra("seed", nextItem.seed)
                putExtra("size", nextItem.width)
                putExtra("use_opencl", useOpenCL)
                nextItem.denoiseStrength?.let {
                    putExtra("denoise_strength", it)
                }
            }
            
            context.startForegroundService(intent)
            
            // Wait for generation to complete
            BackgroundGenerationService.generationState.collect { state ->
                when (state) {
                    is GenerationState.Success -> {
                        batchQueueManager.updateItemStatus(nextItem.id, BatchItemStatus.COMPLETED)
                        return@collect
                    }
                    is GenerationState.Error -> {
                        batchQueueManager.updateItemStatus(nextItem.id, BatchItemStatus.FAILED)
                        return@collect
                    }
                    else -> {
                        // Continue waiting
                    }
                }
            }
            
        } catch (e: Exception) {
            // Mark as failed
            batchQueueManager.updateItemStatus(nextItem.id, BatchItemStatus.FAILED)
        }
        
        // Small delay between generations
        delay(500)
    }
}
