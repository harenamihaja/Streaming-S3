package server;

import csvreader.CSVReader;
import model.VideoMetadata;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class ChildVideoServer implements VideoStorageServer {
    private List<VideoMetadata> videos;
    private Path storageDirectory;
    private String path;
    private String[] extensions;

    public ChildVideoServer(String path) {
        CSVReader.importConfig("conf/conf.csv",this);
        this.storageDirectory = Paths.get(path);
        this.videos = scanVideosInDirectory();
    }

    private List<VideoMetadata> scanVideosInDirectory() {
        List<VideoMetadata> foundVideos = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(storageDirectory)) {
            foundVideos = paths
                    .filter(Files::isRegularFile) // Filtre pour ne garder que les fichiers
                    .filter(path -> {
                        String fileName = path.toString().toLowerCase();
                        return Arrays.stream(extensions)
                                .anyMatch(fileName::endsWith); // Vérifie si l'extension correspond
                    })
                    .map(VideoMetadata::new)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return foundVideos;
    }

    @Override
    public List<VideoMetadata> getAvailableVideos() {
        return videos;
    }

    @Override
    public void streamVideo(String videoId, OutputStream clientOutputStream) {

    }
}