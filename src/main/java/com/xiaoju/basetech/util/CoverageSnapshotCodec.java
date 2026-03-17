package com.xiaoju.basetech.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoju.basetech.entity.CoverageSnapshotEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CoverageSnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CoverageSnapshotCodec() {
    }

    public static byte[] toGzipBytes(CoverageSnapshotEntity snapshot) {
        try {
            byte[] jsonBytes = OBJECT_MAPPER.writeValueAsBytes(snapshot);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(out)) {
                gzipOutputStream.write(jsonBytes);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode coverage snapshot.", e);
        }
    }

    public static CoverageSnapshotEntity fromGzipBytes(byte[] gzipBytes) {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
            return OBJECT_MAPPER.readValue(gzipInputStream, CoverageSnapshotEntity.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode coverage snapshot.", e);
        }
    }
}
