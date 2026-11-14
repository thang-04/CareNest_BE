package com.carenest.utils;

import com.carenest.exception.GlobalException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LearningResourcePolicy {
    private LearningResourcePolicy() {}

    private static final Set<String> YOUTUBE = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final Set<String> GOOGLE = Set.of("drive.google.com", "docs.google.com");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern QUERY_V = Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)");
    private static final Pattern PATH_ID = Pattern.compile("^/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})(?:[/?].*)?$");

    public static String safeName(String name) {
        String clean = (name == null ? "file" : name).replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .strip();
        if (clean.isBlank() || clean.length() > 255) throw GlobalException.of(ApiCode.FILE_NAME_INVALID);
        return clean;
    }

    /**
     * EDU-83: chỉ nhận https YouTube (chuẩn hóa về {@code watch?v=<id>}) và Google Drive/Docs; không gọi dịch vụ ngoài.
     */
    public static String normalizeLink(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty() || text.length() > 2000) throw GlobalException.of(ApiCode.RESOURCE_LINK_INVALID);
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException ex) {
            throw GlobalException.of(ApiCode.RESOURCE_LINK_INVALID);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null)
            throw GlobalException.of(ApiCode.RESOURCE_LINK_INVALID);
        if (GOOGLE.contains(host)) return text;
        String id = null;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if ("youtu.be".equals(host)) {
            String first = path.length() > 1 ? path.substring(1).split("/")[0] : "";
            if (VIDEO_ID.matcher(first).matches()) id = first;
        } else if (YOUTUBE.contains(host)) {
            Matcher q = QUERY_V.matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
            Matcher p = PATH_ID.matcher(path);
            if ("/watch".equals(path) && q.find()) id = q.group(1);
            else if (p.matches()) id = p.group(1);
        }
        if (id == null) throw GlobalException.of(ApiCode.RESOURCE_LINK_INVALID);
        return "https://www.youtube.com/watch?v=" + id;
    }
}
