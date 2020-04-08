package com.geekbrains.cloud.storage.common;

import com.sun.source.tree.BreakTree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utility {
    private static final Logger logger = (Logger) LogManager.getLogger(Utility.class);

    public static Map<String, String> createMapWithFileNameAndSize(Path path, Object rootFolder, Object currentFolder) throws IOException {
        Map<String, String> mapFileNameAndSize = new LinkedHashMap<>();
        List<String> listOfServerFolders = Files.list(path)
                .filter(p -> Files.isDirectory(p))
                .map(Path::getFileName)
                .map(path1 -> path1.toString() + File.separator)
                .collect(Collectors.toList());
        if (! rootFolder.equals(currentFolder)){
            mapFileNameAndSize.put("./", "Level_Up");
        }
        listOfServerFolders.forEach(fileName -> mapFileNameAndSize.put(fileName,"folder"));

        List<String> listOfServerFiles = Files.list(path)
                .filter(p -> Files.isRegularFile(p))
                .map(Path::toFile)
                .map(File::getName)
                .collect(Collectors.toList());
        listOfServerFiles.forEach(fileName -> {
            long sizeFileName = 0;
            try {
                sizeFileName = Files.size(Paths.get(currentFolder + fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
            mapFileNameAndSize.put(fileName, String.valueOf(sizeFileName));
        });
        return mapFileNameAndSize;
    }
}
