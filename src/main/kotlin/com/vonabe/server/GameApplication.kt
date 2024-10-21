package com.vonabe.server

import com.vonabe.server.game.service.FoodService
import javafx.application.Application
import javafx.application.Application.launch
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext

@SpringBootApplication
class GameApplication : Application() {

    private lateinit var springContext: ApplicationContext
    private lateinit var foodService: FoodService

    override fun init() {
        // Инициализация Spring контекста
        springContext = SpringApplication.run(GameApplication::class.java)
        foodService = springContext.getBean(FoodService::class.java)
    }

    override fun start(primaryStage: Stage) {
        // Create an ImageView to display the image
        val imageView = ImageView()

        var frequency = 0.856f
        var noiseFreq = 0.6f
        var reductionStep = 10

        // Slider for Frequency
        val frequencySlider = createSlider(0.0001, 3.0, 0.1, "Frequency")
        frequencySlider.children.filterIsInstance<Slider>().first().valueProperty().addListener { _, _, newValue ->
            // Update frequency based on slider value
//            noiseMapGenerator.generateAndSavePreview()
//            println("Frequency: $newValue")
            frequency = newValue.toFloat()
            val imageBuffered = foodService.generateFood(frequency, reductionStep, noiseFreq)
            imageView.image = SwingFXUtils.toFXImage(imageBuffered, null)
        }

        // Slider for Reduction Step
        val reductionStepSlider = createSlider(1.0, 500.0, 50.0, "Reduction Step")
        reductionStepSlider.children.filterIsInstance<Slider>().first().valueProperty().addListener { _, _, newValue ->
            // Update reduction step based on slider value
            reductionStep = newValue.toInt()
            val imageBuffered = foodService.generateFood(frequency, reductionStep, noiseFreq)
            imageView.image = SwingFXUtils.toFXImage(imageBuffered, null)
        }

        // Slider for Noise Frequency
        val noiseFreqSlider = createSlider(0.0001, 3.0, 0.6, "Noise Frequency")
        noiseFreqSlider.children.filterIsInstance<Slider>().first().valueProperty().addListener { _, _, newValue ->
            // Update noise frequency based on slider value
            noiseFreq = newValue.toFloat()
            val imageBuffered = foodService.generateFood(frequency, reductionStep, noiseFreq)
            imageView.image = SwingFXUtils.toFXImage(imageBuffered, null)
        }

        // Layout to hold image and sliders
        val root = StackPane(imageView, VBox(10.0, frequencySlider, reductionStepSlider, noiseFreqSlider))
        root.padding = Insets(10.0)

        val scene = Scene(root, 1920.0, 1080.0)
        primaryStage.title = "Generate Noise Map"
        primaryStage.scene = scene
        primaryStage.show()
    }
}

// Helper method to create a slider with a label
private fun createSlider(min: Double, max: Double, initial: Double, label: String): VBox {
    val slider = Slider(min, max, initial)
    slider.orientation = Orientation.HORIZONTAL
    slider.isShowTickLabels = true
    slider.isShowTickMarks = true

    val sliderLabel = Label("$label: ${slider.value}")
    sliderLabel.textFill = Color.WHITE
    slider.valueProperty().addListener { _, _, newValue ->
        sliderLabel.text = "$label: ${newValue.toDouble()}"
    }
    return VBox(5.0, sliderLabel, slider)
}

fun main(args: Array<String>) {
//    runApplication<GameApplication>(*args)
    launch(GameApplication::class.java, *args)
//    launch(GameApplication::class.java)
}
