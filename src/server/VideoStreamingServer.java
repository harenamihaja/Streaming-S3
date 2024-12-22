package server;

import csvreader.CSVReader;
import model.VideoMetadata;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoStreamingServer {
    private static int PORT;
    private static int CHUNK_SIZE;

    private CentralVideoServer centralServer;

    public VideoStreamingServer() {
        CSVReader.importConfig("conf/conf.csv", this);
        centralServer = new CentralVideoServer();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Video Streaming Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Handle client connection in a new thread
                new Thread(() -> handleClientConnection(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
        ) {
            // Send available videos to the client
            List<VideoMetadata> availableVideos = centralServer.getAllAvailableVideos();
            out.writeObject(availableVideos);
            out.flush();

            // Wait for video selection or other commands
            while (true) {
                String command = (String) in.readObject();
                System.out.println(command);
                if ("QUIT".equals(command)) {
                    System.out.println("Exiting Streaming");
                    break;
                }
                if (command.startsWith("STREAM:")) {
                    String videoId = command.substring(7);
                    streamVideoToClient(videoId, out, in);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void streamVideoToClient(String videoId, ObjectOutputStream out, ObjectInputStream command) {
        AtomicBoolean stream = new AtomicBoolean(true);
        VideoMetadata video = centralServer.getAllAvailableVideos().stream()
                .filter(v -> v.getId().equals(videoId))
                .findFirst()
                .orElse(null);

        if (video == null) {
            try {
                out.writeObject("VIDEO_ERROR");
                out.flush();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }



        try (BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(video.getFilePath()))) {
            // Send video metadata first
            out.writeObject("START");
            out.writeObject(videoId);
            out.writeObject(video.getExtensions());
            out.writeLong(Files.size(Paths.get(video.getFilePath())));
            out.flush();

            // Stream video in chunks
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            while (((bytesRead = fileInputStream.read(buffer)) != -1) ) {
                out.writeObject("VIDEO_CHUNK");
                out.writeInt(bytesRead);
                out.write(buffer, 0, bytesRead);
                out.flush();
            }

            // Signal end of video stream
                out.writeObject("END");
                out.flush();
                System.out.println("Transfer ended successfully");

        } catch (IOException e) {
            try {
                out.writeObject("ERROR");
                out.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            stream.set(false);
        }
    }

    public static void main(String[] args) {
        new VideoStreamingServer().start();
    }
}