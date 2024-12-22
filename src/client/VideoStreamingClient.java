package client;

import csvreader.CSVReader;
import model.VideoMetadata;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoStreamingClient extends Application {
    private static String SERVER_HOST;
    private static int SERVER_PORT;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private MediaView mediaView;
    private MediaPlayer mediaPlayer;
    private Stage videoStage;
    private Slider progressBar = new Slider();
    private ListView<VideoMetadata> videoList;

    // Chunked streaming variables
    private Path tempVideoFile;
    private BufferedOutputStream tempFileOutputStream;
    private AtomicBoolean isStreamingCancelled = new AtomicBoolean(false);

    @Override
    public void start(Stage primaryStage) {
        try {
            CSVReader.importConfig("conf/conf.csv", this);
            // Establish socket connection
            boolean streaming = true;
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Setup UI
            videoList = new ListView<>();

            // Receive available videos from server
            List<VideoMetadata> availableVideos = (List<VideoMetadata>) in.readObject();
            Platform.runLater(() -> {
                videoList.getItems().addAll(availableVideos);
            });

            // Video selection event
            videoList.setOnMouseClicked(event -> {
                VideoMetadata selectedVideo = videoList.getSelectionModel().getSelectedItem();
                if (selectedVideo != null) {
                    try {
                        // Reset previous streaming setup
                        if (tempVideoFile != null) {
                            tempVideoFile = null;
                        }
                        isStreamingCancelled.set(false);

                        // Request video streaming
                        out.writeObject("STREAM:" + selectedVideo.getId());
                        out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            // Listen for server responses in a separate thread
            new Thread(this::listenForServerResponses).start();

            // Setup JavaFX UI
            VBox layout = new VBox(10);
            layout.getChildren().addAll(videoList);

            Scene scene = new Scene(layout, 800, 600);
            primaryStage.setTitle("Streaming Vidéo Client");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> {
                try {
                    Platform.runLater(()->{
                        isStreamingCancelled.set(false);
                    });
                    out.writeObject("QUIT");
                    out.flush();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            primaryStage.show();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showErrorDialog("Connection Error", "Could not connect to the server.");
        }
    }

    private void listenForServerResponses() {
        try {
            while (true) {
                Object response = in.readObject();
                System.out.println(response);

                if ("START".equals(response)) {
                    // Prepare for video streaming
                    String videoId = (String) in.readObject();
                    String extensions = (String) in.readObject();
                    long fileSize = in.readLong();

                    // Create a temporary file for the video
                    tempVideoFile = Files.createTempFile("streaming", extensions);
                    tempFileOutputStream = new BufferedOutputStream(new FileOutputStream(tempVideoFile.toFile()));

                    // Start a thread to handle progressive video playing
                    new Thread(() -> startProgressiveVideoPlayback()).start();
                } else if ("VIDEO_CHUNK".equals(response)) {
                    // Receive and save video chunk
                    int bytesRead = in.readInt();
                    byte[] buffer = new byte[bytesRead];
                    in.readFully(buffer);

                    if (tempFileOutputStream != null && !isStreamingCancelled.get()) {
                        tempFileOutputStream.write(buffer);
                        tempFileOutputStream.flush();
                    }

                    System.out.println(bytesRead+ " bytes received");
                } else if ("END".equals(response)) {
                    // Close the file stream when video is fully received
                    if (tempFileOutputStream != null) {
                        tempFileOutputStream.close();
                        tempFileOutputStream = null;
                    }
                }
                else if ("ERROR".equals(response)) {
                    Platform.runLater(() -> showErrorDialog("Streaming Error", "Could not stream the video."));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            try{
                if(!isStreamingCancelled.get()){
                    socket = new Socket(SERVER_HOST,SERVER_PORT);
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());
                    new Thread(this::listenForServerResponses).start();
                }
                else{
                    Platform.runLater(() -> showErrorDialog("Connection Lost", "Lost connection to the server."));
                }
            }
            catch (Exception exception){
                System.out.println(exception.getMessage());
            }
        }
    }

    private void startProgressiveVideoPlayback() {
        try {
            // Wait until some initial data is received
            while (tempFileOutputStream == null || tempVideoFile == null ||
                    Files.size(tempVideoFile) < 1024 * 1024) { // Wait until at least 1MB is downloaded
                Thread.sleep(100);
            }

            Platform.runLater(() -> {
                Media media = new Media(tempVideoFile.toUri().toString());

                if (videoStage == null) {
                    videoStage = new Stage();
                }

                Button button = new Button("||");
                button.setOnMouseClicked(event -> {
                    if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                        mediaPlayer.pause();
                        button.setText(">");
                    } else {
                        mediaPlayer.play();
                        button.setText("||");
                    }
                });

                BorderPane rootComponent = new BorderPane();
                mediaView = new MediaView();
                rootComponent.setCenter(mediaView);

                HBox optionsLayout = new HBox(20);
                optionsLayout.getChildren().addAll(button, progressBar);
                rootComponent.setTop(optionsLayout);

                Scene scene = new Scene(rootComponent, 1000, 1000);
                videoStage.setTitle(tempVideoFile.getFileName().toString());
                videoStage.setScene(scene);
                videoStage.show();
                videoStage.setOnCloseRequest(windowEvent -> {
                    try {
                        // Close file streams
                        if (tempFileOutputStream != null) {
                            tempFileOutputStream.close();
                            tempFileOutputStream = null;
                        }

                        // Stop and dispose media player
                        if (mediaPlayer != null) {
                            mediaPlayer.stop();
                            mediaPlayer.dispose();
                        }

                        isStreamingCancelled.set(true);
                        // Attempt to delete temp file (with force close)
                        Files.deleteIfExists(tempVideoFile);

                    } catch (IOException e) {
                        System.out.println("Error while  closing video stage: " + e.getMessage());
                        // Force file deletion on JVM exit
                        tempVideoFile.toFile().deleteOnExit();
                    }
                });

                mediaPlayer = new MediaPlayer(media);
                mediaView.setMediaPlayer(mediaPlayer);

                // Progress bar logic
                AtomicBoolean isDragging = new AtomicBoolean(false);

                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (!isDragging.get()) {
                        double progress = newTime.toSeconds() / mediaPlayer.getTotalDuration().toSeconds();
                        progressBar.setValue(progress * 100);
                    }
                });

                progressBar.setOnMousePressed(event -> isDragging.set(true));
                progressBar.setOnMouseReleased(event -> {
                    isDragging.set(false);
                    double newTime = progressBar.getValue() / 100 * mediaPlayer.getTotalDuration().toSeconds();
                    mediaPlayer.seek(Duration.seconds(newTime));
                });

                progressBar.setOnMouseDragged(event -> {
                    double newTime = progressBar.getValue() / 100 * mediaPlayer.getTotalDuration().toSeconds();
                    mediaPlayer.seek(Duration.seconds(newTime));
                });

                progressBar.setMin(0);
                progressBar.setMax(100);
                progressBar.setPrefWidth(500);
                progressBar.setMajorTickUnit(10);
                progressBar.setShowTickMarks(true);
                progressBar.setShowTickLabels(false);

                mediaPlayer.play();
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showErrorDialog("Video Error", "Could not play the video."));
        }
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}