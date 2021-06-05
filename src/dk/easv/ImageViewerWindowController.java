package dk.easv;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class ImageViewerWindowController implements Initializable
{
    private final List<Image> images = new ArrayList<>();
    private int currentImageIndex = 0;

    @FXML
    Parent root;

    @FXML
    private ImageView imageView;
    @FXML
    private TextField txtSeconds;
    @FXML
    private Button btnStartSlide;
    @FXML
    private Button btnStopSlide;
    @FXML
    private Text text;
    @FXML
    private HBox colorDisplay = new HBox();

    private int seconds = 1;
    private BooleanProperty isActive = new SimpleBooleanProperty(false);

    private static final AtomicReference<ScheduledExecutorService> scheduledExecutorService = new AtomicReference<>();
    private final Thread changeImage = new Thread(() -> {
        handleBtnNextAction();
    });

    @FXML
    private void handleBtnLoadAction()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select image files");
        fileChooser.getExtensionFilters().add(new ExtensionFilter("Images",
                "*.png", "*.jpg", "*.gif", "*.tif", "*.bmp"));
        List<File> files = fileChooser.showOpenMultipleDialog(new Stage());

        if (!files.isEmpty())
        {
            files.forEach((File f) ->
            {
                images.add(new Image(f.toURI().toString()));
            });
            displayImage();
        }
    }

    @FXML
    private void handleBtnPreviousAction()
    {
        if (!images.isEmpty())
        {
            currentImageIndex =
                    (currentImageIndex - 1 + images.size()) % images.size();
            displayImage();
        }
    }

    @FXML
    private void handleBtnNextAction()
    {
        if (!images.isEmpty())
        {
            currentImageIndex = (currentImageIndex + 1) % images.size();
            displayImage();
        }
    }

    private void displayImage()
    {
        if (!images.isEmpty())
        {
            imageView.setImage(images.get(currentImageIndex));
            File file = new File(images.get(currentImageIndex).getUrl());
            text.setText(file.getName().split("\\.")[0]);

            go(images.get(currentImageIndex), 6);
        }
    }

    public void go(Image image, int threads) {
        Instant start = Instant.now();
        int width = (int) image.getWidth() / threads;
        List<ImageAnalyzer> anal = new ArrayList<>();
        List<int[]> bit = new ArrayList<>();
        int startIndex = 0;
        for (int i = 1; i <= threads; i++) {
            if (i < threads)
                bit.add(new int[]{startIndex + 1, width * i});
            else
                bit.add(new int[]{startIndex + 1, (int) image.getWidth()});
            startIndex = width * i;
        }
        bit.forEach(b -> {
            anal.add(new ImageAnalyzer(image, b[0], b[1], 0, (int) image.getHeight()));
        });
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        try {
            List<Future<Map<String, Integer>>> results = executorService.invokeAll(anal);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<String> colors = new ArrayList<>(ImageAnalyzer.getColorMap().keySet());
        colors.sort(Comparator.comparingInt(c -> ImageAnalyzer.getColorMap().get(c)).reversed());
        Platform.runLater(new Thread(() -> {
            AtomicInteger j = new AtomicInteger(0);
            ImageAnalyzer.getColorMap().values().forEach(j::addAndGet);
            colorDisplay.getChildren().clear();
            for (String color : colors) {
                Circle c = new Circle(8, Paint.valueOf(color));
                Text t = new Text(String.format("%s(%.02f%%)", color, (float) ImageAnalyzer.getColorMap().get(color) * 100 / j.get()));
                colorDisplay.setAlignment(Pos.CENTER);
                colorDisplay.setSpacing(10);
                colorDisplay.getChildren().addAll(Arrays.asList(c, t));
            }
            ImageAnalyzer.clearColorMap();
        }));
        executorService.shutdown();

        Instant end = Instant.now();
        System.out.println(Duration.between(start, end).toMillis() + " ms");
    }

    public void handleStartSlideShow(Thread t, int seconds){
        if (scheduledExecutorService.get() != null && !scheduledExecutorService.get().isShutdown()){
            scheduledExecutorService.get().shutdownNow();
        }
        scheduledExecutorService.set(Executors.newSingleThreadScheduledExecutor());
        scheduledExecutorService.get().scheduleAtFixedRate(changeImage, 0, seconds, TimeUnit.SECONDS);
        isActive.set(true);
    }

    @FXML
    private void handleStopSlide(){
        if (scheduledExecutorService.get() != null)
            scheduledExecutorService.get().shutdownNow();
        isActive.set(false);
    }

    @FXML
    private void handleSetSeconds(){
        if (Integer.valueOf(txtSeconds.getText()) != null){
            seconds = Integer.valueOf(txtSeconds.getText());
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        btnStartSlide.setOnAction(v -> {
            handleStartSlideShow(changeImage, seconds);
        });

        btnStopSlide.setOnAction(v -> {
            handleStopSlide();
        });
    }
}