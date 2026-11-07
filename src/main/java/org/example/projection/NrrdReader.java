package org.example.projection;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Reader for attached, scalar 3-D NRRD files with raw/gzip encoding and
 * unsigned 8-, 16-, or 32-bit voxels, plus nonnegative signed 16-bit data.
 * Signed short is accepted when every actual voxel is >= 0 (as in the
 * lecturer's MRHead_1.nrrd); negative values are rejected explicitly
 * because the projection pipeline uses unsigned samples.
 *
 * NRRD's first axis changes fastest. For sizes sx, sy, sz, the voxel at
 * (x,y,z) is stored at x + sx * (y + sy*z), matching ArrayVolume.
 *
 * Deliberately unsupported (with explicit errors): detached data files,
 * nonzero byte/line skips, other encodings, negative signed values / floating types,
 * and non-3-D data. Spatial metadata is not applied: points are voxel indices.
 */
public final class NrrdReader {
    private static final int MAX_HEADER_BYTES = 1024 * 1024;
    private static final int MAX_LINE_BYTES = 64 * 1024;
    private static final int IO_CHUNK_BYTES = 64 * 1024;
    private static final Set<String> SUPPORTED_MAGIC = Set.of(
            "NRRD0001", "NRRD0002", "NRRD0003", "NRRD0004", "NRRD0005");

    /** Load and decode the NRRD file without changing its unsigned values. */
    public Volume read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
            Header h = readHeader(raw);
            InputStream payload = h.gzip ? new GZIPInputStream(raw, IO_CHUNK_BYTES) : raw;
            // When gzip is used, closing payload also closes raw. Closing it a
            // second time by try-with-resources is harmless.
            try (InputStream dataStream = payload) {
                Volume result = decode(dataStream, h);
                if (dataStream.read() != -1) {
                    throw new IOException("Payload is longer than the dimensions and type declare");
                }
                return result;
            }
        } catch (IOException e) {
            throw new IOException("Cannot load NRRD '" + file + "': " + e.getMessage(), e);
        }
    }

    private static Header readHeader(InputStream input) throws IOException {
        String magic = readAsciiLine(input, new int[]{0});
        if (!SUPPORTED_MAGIC.contains(magic)) {
            throw new IOException("Invalid or unsupported NRRD magic: " + magic);
        }
        Map<String, String> fields = new HashMap<>();
        int[] totalHeaderBytes = {magic.length() + 1};
        while (true) {
            String line = readAsciiLine(input, totalHeaderBytes);
            if (line == null) {
                throw new IOException("NRRD header lacks a blank line before the data");
            }
            if (line.isEmpty()) break;
            if (line.startsWith("#")) continue;
            if (line.contains(":=")) continue; // NRRD custom key/value metadata
            int separator = line.indexOf(':');
            if (separator < 1) {
                throw new IOException("Malformed NRRD header line: " + line);
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (fields.putIfAbsent(key, value) != null) {
                throw new IOException("Duplicate NRRD header field: " + key);
            }
        }
        if (fields.containsKey("data file") || fields.containsKey("datafile")) {
            throw new IOException("Detached NRRD 'data file' is not supported yet; use an attached .nrrd file");
        }
        requireZeroOrAbsent(fields, "line skip");
        requireZeroOrAbsent(fields, "byte skip");

        int dimension = parsePositiveInt(required(fields, "dimension"), "dimension");
        if (dimension != 3) {
            throw new IOException("Only 3-dimensional NRRD volumes are supported; dimension=" + dimension);
        }
        String[] sizeTokens = required(fields, "sizes").split("\\s+");
        if (sizeTokens.length != 3) {
            throw new IOException("'sizes' must contain exactly 3 positive integers");
        }
        int sx = parsePositiveInt(sizeTokens[0], "sizes[0]");
        int sy = parsePositiveInt(sizeTokens[1], "sizes[1]");
        int sz = parsePositiveInt(sizeTokens[2], "sizes[2]");
        if (sx > 1023 || sy > 1023 || sz > 1023) {
            throw new IOException("Each NRRD dimension must be between 1 and 1023 (assignment constraint)");
        }
        long elements = (long) sx * sy * sz;
        if (elements > Integer.MAX_VALUE - 8L) {
            throw new IOException("Volume is too large for a single Java array: " + elements + " voxels");
        }

        String voxelType = required(fields, "type");
        boolean signedShort = isSignedShort(voxelType);
        int bytesPerVoxel = parseType(voxelType);
        String encoding = required(fields, "encoding").toLowerCase(Locale.ROOT);
        boolean gzip;
        if (encoding.equals("raw")) gzip = false;
        else if (encoding.equals("gzip") || encoding.equals("gz")) gzip = true;
        else throw new IOException("Unsupported NRRD encoding: " + encoding + " (use raw or gzip)");

        boolean little = true;
        if (bytesPerVoxel > 1) {
            String endian = required(fields, "endian").toLowerCase(Locale.ROOT);
            if (!endian.equals("little") && !endian.equals("big")) {
                throw new IOException("Invalid NRRD endian: " + endian);
            }
            little = endian.equals("little");
        }
        return new Header(sx, sy, sz, (int) elements, bytesPerVoxel, gzip, little, signedShort);
    }

    /** Read the text header a byte at a time to avoid buffering into the binary payload. */
    private static String readAsciiLine(InputStream in, int[] total) throws IOException {
        byte[] bytes = new byte[MAX_LINE_BYTES];
        int count = 0;
        int next;
        while ((next = in.read()) != -1) {
            total[0]++;
            if (total[0] > MAX_HEADER_BYTES) throw new IOException("NRRD header exceeds 1 MiB");
            if (next == '\n') break;
            if (count == bytes.length) throw new IOException("NRRD header line exceeds 64 KiB");
            if (next > 127) throw new IOException("NRRD header must use ASCII text");
            bytes[count++] = (byte) next;
        }
        if (next == -1 && count == 0) return null;
        if (next == -1) throw new IOException("Unexpected EOF inside NRRD header");
        if (count > 0 && bytes[count - 1] == '\r') count--;
        return new String(bytes, 0, count, StandardCharsets.US_ASCII);
    }

    private static String required(Map<String, String> fields, String field) throws IOException {
        String value = fields.get(field);
        if (value == null || value.isBlank()) throw new IOException("Missing NRRD field: " + field);
        return value;
    }

    private static int parsePositiveInt(String text, String field) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
            // Descriptive IOException below.
        }
        throw new IOException("Invalid positive integer for " + field + ": " + text);
    }

    private static void requireZeroOrAbsent(Map<String, String> fields, String field) throws IOException {
        if (fields.containsKey(field) && !fields.get(field).trim().equals("0")) {
            throw new IOException("Unsupported NRRD " + field + ": " + fields.get(field));
        }
    }

    private static boolean isSignedShort(String input) {
        String type = input.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        return type.equals("short") || type.equals("short int")
                || type.equals("signed short") || type.equals("signed short int")
                || type.equals("int16") || type.equals("int16_t");
    }

    private static int parseType(String input) throws IOException {
        String type = input.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        return switch (type) {
            case "uchar", "unsigned char", "uint8", "uint8_t" -> 1;
            case "ushort", "unsigned short", "unsigned short int", "uint16", "uint16_t",
                 "short", "short int", "signed short", "signed short int", "int16", "int16_t" -> 2;
            case "uint", "unsigned int", "uint32", "uint32_t" -> 4;
            default -> throw new IOException("Unsupported NRRD scalar type: " + input
                    + " (supported: unsigned 8/16/32-bit, nonnegative signed 16-bit)");
        };
    }

    private static Volume decode(InputStream input, Header h) throws IOException {
        int n = h.elements;
        if (h.bytesPerVoxel == 1) {
            byte[] pixels = new byte[n];
            readExact(input, pixels, n);
            return new ByteVolume(h.sx, h.sy, h.sz, pixels);
        }
        int bpv = h.bytesPerVoxel;
        byte[] chunk = new byte[IO_CHUNK_BYTES]; // a multiple of both 2 and 4
        int offset = 0;
        if (bpv == 2) {
            short[] pixels = new short[n];
            while (offset < n) {
                int elements = Math.min(n - offset, chunk.length / bpv);
                readExact(input, chunk, elements * bpv);
                for (int i = 0; i < elements; ++i) {
                    int first = chunk[2*i] & 0xff;
                    int second = chunk[2*i + 1] & 0xff;
                    short value = (short) (h.little ? (first | (second << 8))
                            : ((first << 8) | second));
                    if (h.signedShort && value < 0) {
                        throw new IOException("NRRD signed short contains a negative voxel at linear index "
                                + (offset + i) + ": " + value
                                + ". The current projection pipeline requires nonnegative voxels.");
                    }
                    pixels[offset+i] = value;
                }
                offset += elements;
            }
            return new ShortVolume(h.sx, h.sy, h.sz, pixels);
        }
        int[] pixels = new int[n];
        while (offset < n) {
            int elements = Math.min(n - offset, chunk.length / bpv);
            readExact(input, chunk, elements * bpv);
            for (int i = 0; i < elements; ++i) {
                int j = 4*i;
                int b0 = chunk[j] & 0xff;
                int b1 = chunk[j+1] & 0xff;
                int b2 = chunk[j+2] & 0xff;
                int b3 = chunk[j+3] & 0xff;
                pixels[offset+i] = h.little
                        ? (b0 | (b1 << 8) | (b2 << 16) | (b3 << 24))
                        : ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
            }
            offset += elements;
        }
        return new IntVolume(h.sx, h.sy, h.sz, pixels);
    }

    private static void readExact(InputStream in, byte[] buffer, int length) throws IOException {
        int count = in.readNBytes(buffer, 0, length);
        if (count != length) {
            throw new IOException("Truncated NRRD payload: expected " + length
                    + " bytes in this read, received " + count);
        }
    }

    private record Header(int sx, int sy, int sz, int elements,
                          int bytesPerVoxel, boolean gzip, boolean little, boolean signedShort) { }
}
