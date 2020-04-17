package com.gmail.ameliemouillacpro.a2020_luma_poc_camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    // Informations sur l'appareil caméra
    private lateinit var cameraManager:CameraManager
    private var cameraFacing by Delegates.notNull<Int>()
    lateinit var cameraId:String
    var cameraDevice:CameraDevice? = null

    // Callback de gestion de la session et de la caméra
    private lateinit var stateCallback:CameraDevice.StateCallback
    private lateinit var surfaceTextureListener:SurfaceTextureListener
    var cameraCaptureSession:CameraCaptureSession? = null
    var backgroundHandler:Handler? = null
    private var backgroundThread:HandlerThread? = null

    // Variables des éléments UI
    private lateinit var previewSize:Size


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Association des variables de caméra principales
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK

        // Autorisations d'accès à la caméra
        val CAMERA_REQUEST_CODE = 0
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.CAMERA
            ), CAMERA_REQUEST_CODE
        )

        // Gestion de la caméra
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                setUpCamera()
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }

        // Gestion de la session
        stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                this@MainActivity.cameraDevice = cameraDevice
                createPreviewSession()
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
                cameraDevice.close()
                this@MainActivity.cameraDevice = null
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
                cameraDevice.close()
                this@MainActivity.cameraDevice = null
            }
        }
    }

    // Override les fonctions principales
    override fun onResume() {
        super.onResume()
        openBackgroundThread()
        if (myTexture.isAvailable()) {
            setUpCamera()
            openCamera()
        } else {
            myTexture.setSurfaceTextureListener(surfaceTextureListener)
        }
    }
    override fun onStop() {
        super.onStop()
        closeCamera()
        closeBackgroundThread()
    }

    // Gérer la caméra
    private fun openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    private fun closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession!!.close()
            cameraCaptureSession = null
        }
        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }
    }
    private fun setUpCamera() {
        try {
            for (cameraId in cameraManager.getCameraIdList()) {
                val cameraCharacteristics: CameraCharacteristics =
                    cameraManager.getCameraCharacteristics(cameraId)
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ===
                    cameraFacing
                ) {
                    val streamConfigurationMap =
                        cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                        )
                    previewSize =
                        streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)[0]
                    this.cameraId = cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    // Créer les thread
    private fun closeBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundThread?.quitSafely()
            backgroundThread = null
            backgroundHandler = null
        }
    }
    private fun openBackgroundThread() {
        backgroundThread = HandlerThread("camera_background_thread")
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.getLooper())
    }

    // Configurer les session de capture vidéo et de récupération des images en bitmap
    private fun createPreviewSession() {
        try {
            val surfaceTexture = myTexture.surfaceTexture
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(surfaceTexture)
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(previewSurface)
            cameraDevice!!.createCaptureSession(
                Collections.singletonList(previewSurface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (cameraDevice == null) {
                            return
                        }
                        try {

                            val captureRequest = captureRequestBuilder.build()

                            var captureCallback = object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult
                                ) {
                                    super.onCaptureCompleted(session, request, result)
                                    Log.d("Bitmap",myTexture.bitmap.byteCount.toString())
                                    // J'ai réussi à get le bitmap, plus qu'à l'envoyer au serveur (trop facile)
                                }

                            }

                            this@MainActivity.cameraCaptureSession = cameraCaptureSession
                            this@MainActivity.cameraCaptureSession!!.setRepeatingRequest(
                                captureRequest,
                                captureCallback,
                                backgroundHandler
                            )

                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
}