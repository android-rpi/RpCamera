package com.arpi.rpcamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ImageAnalyzer(context: Context, private val listener: RecogListener) :
        ImageAnalysis.Analyzer {
    private var tfliteModel : MappedByteBuffer
    private var labelList = ArrayList<String>()
    private var outputs: Array<FloatArray>

    private lateinit var tflite: Interpreter
    private var initialized = false
    private val lock = ReentrantLock()

    init {
       tfliteModel = FileUtil.loadMappedFile(context, "mobilenet_v1_1_0_224_float.tflite")
       loadLabels(context.assets.open("labels.txt"))
       outputs = Array(1) { FloatArray(labelList.size) }
    }

    private fun loadLabels(stream: InputStream) {
        val reader = BufferedReader(InputStreamReader(stream))
        var line = reader.readLine()
        while(line != null) {
            labelList.add(line)
            line = reader.readLine()
        }
    }

    private lateinit var executorService: ExecutorService

    fun tfInit(useGPU: Boolean): Task<Void?> {
        lock.lock()
        val task = TaskCompletionSource<Void?>()
        if (initialized) {
            task.setResult(null)
        } else {
            executorService = Executors.newSingleThreadExecutor()
            executorService.execute {
                try {
                    val options = Interpreter.Options()
                    if (useGPU) {
                        options.addDelegate(GpuDelegate())
                    }
                    tflite = Interpreter(tfliteModel, options)
                    task.setResult(null)
                } catch (e: IOException) {
                    task.setException(e)
                }
            }
            initialized = true
        }
        lock.unlock()
        return task.task
    }

    fun tfClose() {
        lock.lock()
        if (initialized) {
            executorService.execute {
                tflite?.close()
            }
            executorService.shutdownNow()
            initialized = false
        }
        lock.unlock()
    }

    private var startTime: Long = 0
    private var endTime: Long = 0

    fun classifyAsync(proxy: ImageProxy): Task<Void?> {
        lock.lock()
        if (initialized) {
            executorService.execute {
                val bitmap = getBitmap(proxy)
                if (bitmap == null) {
                    proxy.close()
                } else {
                    convertBitmapToByteBuffer(bitmap)

                    startTime = SystemClock.uptimeMillis()
                    tflite.run(imgData, outputs)
                    endTime = SystemClock.uptimeMillis()

                    proxy.close()
                    listener(getResult())
                }
            }
        }
        val task = TaskCompletionSource<Void?>()
        task.setResult(null)
        lock.unlock()
        return task.task
    }

    override fun analyze(proxy: ImageProxy) {
        classifyAsync(proxy)
        Thread.sleep(300)
    }

    private val RESULTS_TO_SHOW = 4
    private val sortedLabels = PriorityQueue<Map.Entry<String, Float>>(RESULTS_TO_SHOW)
    { o1, o2 -> o1.value.compareTo(o2.value) }

    private fun getResult(): String {
        for (i in 0 until labelList.size) {
            sortedLabels.add(AbstractMap.SimpleEntry(labelList[i], outputs[0][i]))
            if (sortedLabels.size > RESULTS_TO_SHOW) {
                sortedLabels.poll()
            }
        }

        var text = ""
        for (i in 0 until sortedLabels.size) {
            val label = sortedLabels.poll()
            text = String.format("\n   %s: %f", label.key, label.value) + text
        }
        text = "Recognition: " + (endTime - startTime) + " msec \n" + text
        return text
    }


    private var imgData: ByteBuffer? = null
    private val intValues = IntArray(224 * 224)
    private val IMAGE_MEAN = 128.0f
    private val IMAGE_STD = 128.0f

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            imgData = ByteBuffer.allocateDirect(
                    1 * 224 * 224 * 3 * 4)
            imgData!!.order(ByteOrder.nativeOrder())
        }
        imgData!!.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val v: Int = intValues.get(pixel++)
                imgData!!.putFloat(((v shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                imgData!!.putFloat(((v shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                imgData!!.putFloat(((v and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
    }

    private val yuvToRgbConverter = YuvToRgbConverter(context)
    private lateinit var bitmapBuffer: Bitmap

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun getBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        if (!::bitmapBuffer.isInitialized) {
            bitmapBuffer = Bitmap.createBitmap(
                    640, 480, Bitmap.Config.ARGB_8888
            )
        }
        yuvToRgbConverter.yuvToRgb(image, bitmapBuffer)

        val scale = Matrix()
        scale.setScale(224/480f, 224/480f)
        return Bitmap.createBitmap(bitmapBuffer, 80, 0, 480, 480, scale, false)
    }
}