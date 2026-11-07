package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NrrdReaderTest {
    @TempDir Path dir;
    private final NrrdReader reader = new NrrdReader();

    @Test void rawUnsigned8PreservesAxisOrderAndUnsignedValues() throws IOException {
        // x changes fastest: x + sx*(y + sy*z)
        byte[] values = {0, 1, 2, (byte)255, 4, 5, 6, 7};
        Volume v = reader.read(write("u8.nrrd", "unsigned char", "raw", null,
                "2 2 2", values, "\n", "# test data\n"));
        assertEquals(2, v.sizeX());
        assertEquals(2, v.sizeY());
        assertEquals(2, v.sizeZ());
        assertEquals(255, v.getVoxel(1, 1, 0));
        assertEquals(7, v.getVoxel(1, 1, 1));
    }

    @Test void gzipUnsigned8CanDecodePayload() throws IOException {
        byte[] values = {11, (byte)255, 22, 33};
        Volume v = reader.read(write("u8gz.nrrd", "uint8", "gzip", null,
                "2 2 1", gzip(values), "\n", ""));
        assertEquals(255, v.getVoxel(1,0,0));
        assertEquals(33, v.getVoxel(1,1,0));
    }

    @Test void rawUnsigned16LittleEndian() throws IOException {
        Volume v = reader.read(write("u16le.nrrd", "unsigned short", "raw", "little",
                "2 1 1", new byte[]{0x34,0x12,(byte)0xff,(byte)0xff}, "\n", ""));
        assertEquals(0x1234, v.getVoxel(0,0,0));
        assertEquals(65535, v.getVoxel(1,0,0));
    }

    @Test void gzipUnsigned16BigEndianAndCrLf() throws IOException {
        Volume v = reader.read(write("u16be.nrrd", "uint16", "gz", "big",
                "2 1 1", gzip(new byte[]{0x12,0x34,(byte)0xff,(byte)0xff}), "\r\n", ""));
        assertEquals(0x1234, v.getVoxel(0,0,0));
        assertEquals(65535, v.getVoxel(1,0,0));
    }

    @Test void unsigned32LittleAndBigEndianness() throws IOException {
        Volume le = reader.read(write("u32le.nrrd", "uint32", "raw", "little",
                "2 1 1", new byte[]{1,0,0,0,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff}, "\n", ""));
        Volume be = reader.read(write("u32be.nrrd", "unsigned int", "raw", "big",
                "2 1 1", new byte[]{0,0,0,1,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff}, "\n", ""));
        for (Volume v : new Volume[]{le, be}) {
            assertEquals(1, v.getVoxel(0,0,0));
            assertEquals(4_294_967_295L, v.getVoxel(1,0,0));
        }
    }

    @Test void rejectsTruncatedData() throws IOException {
        Path file = write("truncated.nrrd", "uint8", "raw", null,
                "2 2 1", new byte[]{1,2,3}, "\n", "");
        assertTrue(assertThrows(IOException.class, () -> reader.read(file)).getMessage().contains("Truncated"));
    }

    @Test void rejectsExcessData() throws IOException {
        Path file = write("extra.nrrd", "uint8", "raw", null,
                "2 1 1", new byte[]{1,2,3}, "\n", "");
        assertTrue(assertThrows(IOException.class, () -> reader.read(file)).getMessage().contains("longer"));
    }

    @Test void rejectsUnsupportedFloatingType() throws IOException {
        Path file = write("badtype.nrrd", "float", "raw", null,
                "1 1 1", new byte[]{1}, "\n", "");
        assertTrue(assertThrows(IOException.class, () -> reader.read(file)).getMessage()
                .contains("Unsupported NRRD scalar type"));
    }

    @Test void signedShortWithNonnegativeVoxelsIsAccepted() throws IOException {
        Volume volume = reader.read(write("mrhead-mini.nrrd", "short", "gzip", "little",
                "2 1 1", gzip(new byte[]{0, 0, 0x17, 0x01}), "\n", ""));
        assertEquals(0L, volume.getVoxel(0, 0, 0));
        assertEquals(279L, volume.getVoxel(1, 0, 0));
    }

    @Test void signedShortWithNegativeVoxelsIsRejectedRatherThanMisreadAsUnsigned() throws IOException {
        Path file = write("negative-short.nrrd", "short", "raw", "little",
                "2 1 1", new byte[]{0, 0, (byte) 0xff, (byte) 0xff}, "\n", "");
        IOException error = assertThrows(IOException.class, () -> reader.read(file));
        assertTrue(error.getMessage().contains("negative voxel"));
    }

    @Test void rejectsUnsupportedDimensionsAndMissingEndian() throws IOException {
        Path badDimension = write("dimension.nrrd", "uint8", "raw", null,
                "2 2 2", new byte[8], "\n", "dimension: 4\n");
        assertThrows(IOException.class, () -> reader.read(badDimension));
        Path noEndian = write("endian.nrrd", "uint16", "raw", null,
                "1 1 1", new byte[2], "\n", "");
        assertTrue(assertThrows(IOException.class, () -> reader.read(noEndian)).getMessage().contains("endian"));
    }

    @Test void rejectsDetachedDataAndSkips() throws IOException {
        Path detached = write("detached.nrrd", "uint8", "raw", null,
                "1 1 1", new byte[]{1}, "\n", "data file: voxels.raw\n");
        assertTrue(assertThrows(IOException.class, () -> reader.read(detached)).getMessage().contains("Detached"));
        Path skip = write("skip.nrrd", "uint8", "raw", null,
                "1 1 1", new byte[]{1}, "\n", "byte skip: 1\n");
        assertTrue(assertThrows(IOException.class, () -> reader.read(skip)).getMessage().contains("byte skip"));
    }

    @Test void rejectsBadMagicAndMissingHeaderDelimiter() throws IOException {
        Path magic = dir.resolve("magic.nrrd");
        Files.writeString(magic, "BAD00005\ntype: uint8\n\n");
        assertTrue(assertThrows(IOException.class, () -> reader.read(magic)).getMessage().contains("magic"));
        Path noDelimiter = dir.resolve("no-delimiter.nrrd");
        Files.writeString(noDelimiter, "NRRD0005\ntype: uint8\n");
        assertThrows(IOException.class, () -> reader.read(noDelimiter));
    }

    private Path write(String name, String type, String encoding, String endian,
                       String sizes, byte[] payload, String lineEnding, String extra) throws IOException {
        String header = "NRRD0005" + lineEnding
                + "type: " + type + lineEnding
                + "dimension: 3" + lineEnding
                + "sizes: " + sizes + lineEnding
                + "encoding: " + encoding + lineEnding
                + (endian == null ? "" : "endian: " + endian + lineEnding)
                + extra.replace("\n", lineEnding)
                + lineEnding;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        Path file = dir.resolve(name);
        Files.write(file, out.toByteArray());
        return file;
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }
}
