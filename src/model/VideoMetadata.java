package model;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class VideoMetadata implements Serializable {
    private String id;
    private String title;
    private String filePath;
    private long fileSize;

    public VideoMetadata() {
        this.id = UUID.randomUUID().toString();
    }

    // Constructeurs, getters et setters
    public VideoMetadata(Path videoPath) {
        this.id = UUID.randomUUID().toString();
        this.title = videoPath.getFileName().toString();
        this.filePath = videoPath.toString();
        try {
            this.fileSize = Files.size(videoPath);
        } catch (java.io.IOException e) {
            this.fileSize = -1;
        }
    }

    // Getters et setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    @Override
    public String toString() {
        return title;
    }

    public String getExtensions(){
        String result = ".mp4";
        File videoFile = new File(filePath);
        if(videoFile.getName().endsWith(".mp3")) result = ".mp3";
        return result;
    }

}