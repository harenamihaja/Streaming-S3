package server;

import csvreader.CSVReader;
import model.VideoMetadata;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;

public class CentralVideoServer {
    private List<VideoStorageServer> childServers;
    String[] paths;

    public CentralVideoServer() {
        CSVReader.importConfig("conf/conf.csv",this);
        childServers = new ArrayList<>();
        // Initialiser les serveurs filles sur différents répertoires
        for (int i = 0; i < paths.length; i++) {
            childServers.add(new ChildVideoServer(paths[i]));
        }
    }

    public List<VideoMetadata> getAllAvailableVideos() {
        return childServers.stream()
                .flatMap(server -> server.getAvailableVideos().stream())
                .collect(Collectors.toList());
    }

}