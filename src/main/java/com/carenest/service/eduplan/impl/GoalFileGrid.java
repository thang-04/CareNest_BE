package com.carenest.service.eduplan.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.TableIterator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Đọc bảng Word/Excel/HTML/CSV thành lưới ô; ô gộp dọc giữ cờ continued để không nhân đôi mục tiêu (EDU-46).
 */
final class GoalFileGrid {

    /** Một ô sau khi trải gộp. */
    record Cell(String text, boolean continued) {
        static final Cell EMPTY = new Cell("", false);
    }

    /** Bảng = các dòng ô; cột thiếu coi như ô trống. */
    record Table(List<List<Cell>> rows) {
        Cell at(int row, int col) {
            List<Cell> r = rows.get(row);
            return col < r.size() && r.get(col) != null ? r.get(col) : Cell.EMPTY;
        }

        int width() {
            return rows.stream().mapToInt(List::size).max().orElse(0);
        }
    }

    // Giới hạn dữ liệu mở rộng trước khi cấp phát DOM, kể cả entry không dùng.
    private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 256;
    private static final int MAX_DOC_BYTES = 2 * 1024 * 1024;
    private static final int MAX_COLUMNS = 256;
    private static final byte[] OLE_HEADER = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final Pattern HTML_TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b([^<>]*+)>");
    private static final Pattern XLSX_REF = Pattern.compile("([A-Z]+)(\\d+)");

    private GoalFileGrid() {}

    // Chỉ phần đầu để nhận diện kế hoạch; không trả văn bản/người ký ra preview.
    static String leadingText(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (startsWith(bytes, OLE_HEADER)) {
                try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
                    var range = document.getRange();
                    StringBuilder head = new StringBuilder();
                    for (int i = 0; i < range.numParagraphs() && head.length() < 8192; i++) {
                        var paragraph = range.getParagraph(i);
                        if (paragraph.isInTable() && goalHeader(paragraph.text())) break;
                        if (!paragraph.isInTable())
                            head.append(paragraph.text()).append('\n');
                    }
                    text = head.toString();
                }
            } else if (bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K' && name.endsWith(".docx")) {
                byte[] xml = zipEntry(bytes, "word/document.xml");
                Document document = parseXml(xml);
                NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
                StringBuilder head = new StringBuilder();
                for (int i = 0; i < paragraphs.getLength() && head.length() < 8192; i++) {
                    Node paragraph = paragraphs.item(i);
                    Node parent = paragraph.getParentNode();
                    while (parent != null && !"tbl".equals(parent.getLocalName())) parent = parent.getParentNode();
                    if (parent != null && goalHeader(paragraph.getTextContent())) break;
                    if (parent == null) head.append(paragraph.getTextContent()).append('\n');
                }
                text = head.toString();
            } else if (name.endsWith(".doc") || name.endsWith(".html") || name.endsWith(".htm")) {
                String html = decode(bytes);
                StringBuilder outside = new StringBuilder();
                int cursor = 0;
                boolean goalTable = false;
                for (HtmlBlock table : htmlBlocks(html, GoalFileTable.MAX_ROWS, "table")) {
                    outside.append(html, cursor, table.start());
                    String header = Normalizer.normalize(htmlText(table.body(html)), Normalizer.Form.NFC)
                            .toLowerCase(Locale.ROOT);
                    if (header.contains("mục tiêu") && header.contains("nội dung")) {
                        goalTable = true;
                        break;
                    }
                    cursor = table.end();
                }
                if (!goalTable) outside.append(html, cursor, html.length());
                text = htmlText(outside.toString());
            } else {
                return "";
            }
            return text.substring(0, Math.min(text.length(), 8192)).replace('\r', '\n');
        } catch (IOException | SAXException | ParserConfigurationException ex) {
            throw GlobalException.withCause(ApiCode.GOAL_FILE_UNSUPPORTED, ex);
        }
    }

    private static boolean goalHeader(String text) {
        String heading = Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .strip();
        return heading.startsWith("mục tiêu") || heading.startsWith("nội dung");
    }

    /** Đọc theo đuôi file và nội dung; định dạng không hỗ trợ ⇒ GOAL_FILE_UNSUPPORTED. */
    static List<Table> read(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean zip = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
        try {
            if (startsWith(bytes, OLE_HEADER)) {
                if (!name.endsWith(".doc")) {
                    throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
                }
                return binaryDoc(bytes);
            }
            if (zip && name.endsWith(".xlsx")) {
                return List.of(xlsx(bytes));
            }
            if (zip) {
                return docx(bytes);
            }
            String text = decode(bytes);
            if (name.endsWith(".csv")) {
                return List.of(csv(text));
            }
            if (name.endsWith(".doc") || name.endsWith(".htm") || name.endsWith(".html")) {
                return html(text);
            }
        } catch (IOException | SAXException | ParserConfigurationException | RuntimeException ex) {
            if (ex instanceof GlobalException ge) {
                throw ge;
            }
            throw GlobalException.withCause(ApiCode.GOAL_FILE_UNSUPPORTED, ex);
        }
        throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
    }

    private static List<Table> binaryDoc(byte[] bytes) throws IOException {
        if (bytes.length > MAX_DOC_BYTES) {
            throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
        }
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            List<Table> tables = new ArrayList<>();
            TableIterator iterator = new TableIterator(document.getRange());
            int rowCount = 0;
            long textLength = 0;
            while (iterator.hasNext()) {
                var source = iterator.next();
                rowCount += source.numRows();
                if (rowCount > GoalFileTable.MAX_ROWS) {
                    throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                }
                // Ô gộp ngang làm số ô mỗi dòng khác nhau; dùng vị trí biên để giữ đúng cột.
                TreeSet<Integer> edges = new TreeSet<>();
                for (int r = 0; r < source.numRows(); r++) {
                    var sourceRow = source.getRow(r);
                    if (sourceRow.numCells() > MAX_COLUMNS) {
                        throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                    }
                    for (int c = 0; c < sourceRow.numCells(); c++) {
                        var cell = sourceRow.getCell(c);
                        edges.add(cell.getLeftEdge());
                        edges.add(Math.addExact(cell.getLeftEdge(), cell.getWidth()));
                    }
                    if (edges.size() > MAX_COLUMNS + 1) {
                        throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                    }
                }
                List<Integer> columns = new ArrayList<>(edges);
                if (columns.size() < 2) {
                    continue;
                }
                List<List<Cell>> rows = new ArrayList<>();
                for (int r = 0; r < source.numRows(); r++) {
                    var sourceRow = source.getRow(r);
                    List<Cell> row = new ArrayList<>(Collections.nCopies(columns.size() - 1, Cell.EMPTY));
                    for (int c = 0; c < sourceRow.numCells(); c++) {
                        var cell = sourceRow.getCell(c);
                        int col = Collections.binarySearch(columns, cell.getLeftEdge());
                        textLength += (long) cell.getEndOffset() - cell.getStartOffset();
                        if (textLength > MAX_ENTRY_BYTES / 2) {
                            throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                        }
                        if (cell.isMerged() && !cell.isFirstMerged()) {
                            continue;
                        }
                        if (cell.isVerticallyMerged() && !cell.isFirstVerticallyMerged() && !rows.isEmpty()) {
                            row.set(col, new Cell(cellAt(rows.getLast(), col).text(), true));
                        } else {
                            String text = cell.text()
                                    .replace("\u0007", "")
                                    .replace('\r', '\n')
                                    .replace('\u000B', '\n');
                            row.set(col, new Cell(text.strip(), false));
                        }
                    }
                    rows.add(row);
                }
                tables.add(new Table(rows));
            }
            return tables;
        }
    }

    /* ---------- Word .docx ---------- */

    private static List<Table> docx(byte[] bytes) throws IOException, SAXException, ParserConfigurationException {
        byte[] xml = zipEntry(bytes, "word/document.xml");
        if (xml == null) {
            throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
        }
        Document doc = parseXml(xml);
        List<Table> tables = new ArrayList<>();
        NodeList tbls = doc.getElementsByTagNameNS("*", "tbl");
        int rowCount = 0;
        for (int i = 0; i < tbls.getLength(); i++) {
            Element tbl = (Element) tbls.item(i);
            // Bảng lồng trong ô: đọc riêng ở vòng sau, không trộn vào bảng ngoài
            List<List<Cell>> rows = new ArrayList<>();
            for (Element tr : children(tbl, "tr")) {
                requireGridSize(++rowCount, 0);
                List<Cell> row = new ArrayList<>();
                for (Element tc : children(tr, "tc")) {
                    Element pr = first(tc, "tcPr");
                    int span = 1;
                    String vMerge = null;
                    if (pr != null) {
                        Element gs = first(pr, "gridSpan");
                        if (gs != null) {
                            span = Math.max(1, parseInt(attr(gs, "val"), 1));
                        }
                        Element vm = first(pr, "vMerge");
                        if (vm != null) {
                            vMerge = attr(vm, "val");
                            if (vMerge == null || vMerge.isEmpty()) {
                                vMerge = "continue";
                            }
                        }
                    }
                    int col = row.size();
                    if (span > MAX_COLUMNS - col) {
                        throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                    }
                    if ("continue".equals(vMerge) && !rows.isEmpty()) {
                        Cell above = cellAt(rows.get(rows.size() - 1), col);
                        row.add(new Cell(above.text(), true));
                    } else {
                        row.add(new Cell(cellText(tc), false));
                    }
                    for (int k = 1; k < span; k++) {
                        row.add(Cell.EMPTY);
                    }
                }
                rows.add(row);
            }
            tables.add(new Table(rows));
        }
        return tables;
    }

    private static String cellText(Element tc) {
        List<String> paragraphs = new ArrayList<>();
        for (Element p : children(tc, "p")) {
            StringBuilder sb = new StringBuilder();
            NodeList all = p.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < all.getLength(); i++) {
                Element el = (Element) all.item(i);
                switch (el.getLocalName()) {
                    case "t" -> sb.append(el.getTextContent());
                    case "tab" -> sb.append(' ');
                    case "br", "cr" -> sb.append('\n');
                    default -> {
                        // các thẻ định dạng khác không mang chữ
                    }
                }
            }
            paragraphs.add(sb.toString());
        }
        return String.join("\n", paragraphs).strip();
    }

    /* ---------- Excel .xlsx ---------- */

    private static Table xlsx(byte[] bytes) throws IOException, SAXException, ParserConfigurationException {
        byte[] sheet = zipEntry(bytes, "xl/worksheets/sheet1.xml");
        if (sheet == null) {
            throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
        }
        List<String> shared = new ArrayList<>();
        byte[] sst = zipEntry(bytes, "xl/sharedStrings.xml");
        if (sst != null) {
            NodeList sis = parseXml(sst).getElementsByTagNameNS("*", "si");
            for (int i = 0; i < sis.getLength(); i++) {
                StringBuilder sb = new StringBuilder();
                NodeList ts = ((Element) sis.item(i)).getElementsByTagNameNS("*", "t");
                for (int k = 0; k < ts.getLength(); k++) {
                    sb.append(ts.item(k).getTextContent());
                }
                shared.add(sb.toString());
            }
        }
        Document doc = parseXml(sheet);
        TreeMap<Integer, Map<Integer, String>> grid = new TreeMap<>();
        NodeList cells = doc.getElementsByTagNameNS("*", "c");
        for (int i = 0; i < cells.getLength(); i++) {
            Element c = (Element) cells.item(i);
            int[] rc = ref(attr(c, "r"));
            if (rc == null) {
                continue;
            }
            String type = attr(c, "t");
            String value;
            if ("inlineStr".equals(type)) {
                Element is = first(c, "is");
                value = is == null ? "" : is.getTextContent();
            } else {
                Element v = first(c, "v");
                value = v == null ? "" : v.getTextContent();
                if ("s".equals(type)) {
                    int idx = parseInt(value, -1);
                    value = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
                }
            }
            grid.computeIfAbsent(rc[0], k -> new HashMap<>()).put(rc[1], value.strip());
        }
        if (grid.isEmpty()) {
            return new Table(List.of());
        }
        int lastRow = grid.lastKey();
        List<List<Cell>> rows = new ArrayList<>();
        for (int r = 1; r <= lastRow; r++) {
            Map<Integer, String> values = grid.getOrDefault(r, Map.of());
            int width =
                    values.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            List<Cell> row = new ArrayList<>();
            for (int c = 0; c < width; c++) {
                row.add(new Cell(values.getOrDefault(c, ""), false));
            }
            rows.add(row);
        }
        // Ô gộp: ô dưới ô đầu của vùng gộp nhận chữ ô đầu, đánh dấu tiếp nối (cùng mục tiêu)
        NodeList merges = doc.getElementsByTagNameNS("*", "mergeCell");
        for (int i = 0; i < merges.getLength(); i++) {
            String[] range = attr((Element) merges.item(i), "ref").split(":");
            int[] from = ref(range[0]);
            int[] to = range.length > 1 ? ref(range[1]) : from;
            if (from == null || to == null || from[0] < 1 || to[0] > rows.size()) {
                continue;
            }
            String text = cellAt(rows.get(from[0] - 1), from[1]).text();
            for (int r = from[0] + 1; r <= to[0]; r++) {
                List<Cell> row = rows.get(r - 1);
                while (row.size() <= from[1]) {
                    row.add(Cell.EMPTY);
                }
                row.set(from[1], new Cell(text, true));
            }
        }
        return new Table(rows);
    }

    /** "C12" ⇒ {12, 2} (dòng đếm từ 1, cột từ 0). */
    private static int[] ref(String r) {
        if (r == null) {
            return null;
        }
        Matcher m = XLSX_REF.matcher(r);
        if (!m.matches()) {
            return null;
        }
        int col = 0;
        for (char ch : m.group(1).toCharArray()) {
            col = col * 26 + (ch - 'A' + 1);
            requireGridSize(0, col);
        }
        int row = Integer.parseInt(m.group(2));
        requireGridSize(row, col);
        return row < 1 ? null : new int[] {row, col - 1};
    }

    /* ---------- Word .doc do CareNest xuất (HTML) ---------- */

    private static List<Table> html(String text) {
        List<Table> tables = new ArrayList<>();
        int rowCount = 0;
        for (HtmlBlock table : htmlBlocks(text, GoalFileTable.MAX_ROWS, "table")) {
            String tableText = table.body(text);
            List<List<Cell>> rows = new ArrayList<>();
            // Ô rowspan còn phải phủ xuống các dòng sau: cột -> [số dòng còn lại, chữ]
            Map<Integer, Object[]> pending = new HashMap<>();
            for (HtmlBlock sourceRow : htmlBlocks(tableText, GoalFileTable.MAX_ROWS, "tr")) {
                String rowText = sourceRow.body(tableText);
                requireGridSize(++rowCount, 0);
                List<Cell> row = new ArrayList<>();
                int col = 0;
                for (HtmlBlock sourceCell : htmlBlocks(rowText, MAX_COLUMNS, "td", "th")) {
                    while (pending.containsKey(col)) {
                        col = fillPending(row, pending, col);
                    }
                    String attrs = sourceCell.attributes();
                    int colspan = Math.max(1, parseInt(htmlAttr(attrs, "colspan"), 1));
                    int rowspan = Math.max(1, parseInt(htmlAttr(attrs, "rowspan"), 1));
                    if (colspan > MAX_COLUMNS - col || rowspan > GoalFileTable.MAX_ROWS - rows.size()) {
                        throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                    }
                    String cellText = htmlText(sourceCell.body(rowText));
                    set(row, col, new Cell(cellText, false));
                    for (int k = 1; k < colspan; k++) {
                        set(row, col + k, Cell.EMPTY);
                    }
                    if (rowspan > 1) {
                        pending.put(col, new Object[] {rowspan - 1, cellText});
                    }
                    col += colspan;
                }
                // Ô rowspan nằm sau ô cuối của dòng
                List<Integer> rest = new ArrayList<>(pending.keySet());
                rest.sort(Integer::compare);
                for (int c : rest) {
                    if (c >= row.size() || row.get(c) == null) {
                        fillPending(row, pending, c);
                    }
                }
                rows.add(row);
            }
            tables.add(new Table(rows));
        }
        return tables;
    }

    private static int fillPending(List<Cell> row, Map<Integer, Object[]> pending, int col) {
        Object[] p = pending.get(col);
        set(row, col, new Cell((String) p[1], true));
        int left = (Integer) p[0] - 1;
        if (left <= 0) {
            pending.remove(col);
        } else {
            p[0] = left;
        }
        return col + 1;
    }

    private static void set(List<Cell> row, int col, Cell cell) {
        requireGridSize(0, col + 1);
        while (row.size() <= col) {
            row.add(null);
        }
        row.set(col, cell);
    }

    private static String htmlAttr(String attrs, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private record HtmlBlock(int start, int contentStart, int contentEnd, int end, String attributes) {
        String body(String source) {
            return source.substring(contentStart, contentEnd);
        }
    }

    // Chỉ quét thẻ về phía trước; không tìm lại toàn bộ đuôi văn bản khi thiếu thẻ đóng.
    private static List<HtmlBlock> htmlBlocks(String text, int limit, String... tags) {
        var wanted = java.util.Set.of(tags);
        List<HtmlBlock> blocks = new ArrayList<>();
        Matcher matcher = HTML_TAG.matcher(text);
        int start = -1, contentStart = -1;
        String open = null, attributes = null;
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            if (!wanted.contains(name)) continue;
            if (matcher.group(1).isEmpty()) {
                if (open != null) throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
                if (blocks.size() >= limit) throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                start = matcher.start();
                contentStart = matcher.end();
                attributes = matcher.group(3);
                open = name;
            } else if (open != null) {
                if (!open.equals(name)) throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
                blocks.add(new HtmlBlock(start, contentStart, matcher.start(), matcher.end(), attributes));
                open = null;
            }
        }
        if (open != null) throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
        return blocks;
    }

    private static String htmlText(String html) {
        Matcher tags = HTML_TAG.matcher(html);
        StringBuilder plain = new StringBuilder();
        while (tags.find()) {
            String name = tags.group(2).toLowerCase(Locale.ROOT);
            boolean line = name.equals("br")
                    || (!tags.group(1).isEmpty()
                            && java.util.Set.of("p", "div", "li").contains(name));
            tags.appendReplacement(plain, line ? "\n" : "");
        }
        tags.appendTail(plain);
        String s = plain.toString()
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return s.lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /* ---------- CSV ---------- */

    private static Table csv(String text) {
        String first = text.lines().findFirst().orElse("");
        char sep = count(first, ';') > count(first, ',') ? ';' : ',';
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == sep) {
                requireGridSize(rows.size() + 1, row.size() + 1);
                row.add(new Cell(cell.toString().strip(), false));
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                requireGridSize(rows.size() + 1, row.size() + 1);
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(new Cell(cell.toString().strip(), false));
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            requireGridSize(rows.size() + 1, row.size() + 1);
            row.add(new Cell(cell.toString().strip(), false));
            rows.add(row);
        }
        return new Table(rows);
    }

    private static int count(String s, char ch) {
        return (int) s.chars().filter(c -> c == ch).count();
    }

    /* ---------- tiện ích ---------- */

    // Tọa độ và ô gộp trong file nhỏ vẫn có thể yêu cầu cấp phát lưới rất lớn.
    private static void requireGridSize(int rows, int columns) {
        if (rows < 0 || columns < 0 || rows > GoalFileTable.MAX_ROWS || columns > MAX_COLUMNS) {
            throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
        }
    }

    private static String decode(byte[] bytes) {
        int start =
                bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF
                        ? 3
                        : 0;
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Đọc 1 entry zip, chặn file nén quá lớn khi giải (zip bomb). */
    private static byte[] zipEntry(byte[] bytes, String name) throws IOException {
        byte[] result = null;
        long archiveBytes = 0;
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            byte[] buffer = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                boolean requested = name.equals(e.getName());
                if (requested && result != null) throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
                ByteArrayOutputStream out = requested ? new ByteArrayOutputStream() : null;
                long entryBytes = 0;
                int n;
                while ((n = zin.read(buffer)) != -1) {
                    entryBytes += n;
                    archiveBytes += n;
                    if (entryBytes > MAX_ENTRY_BYTES || archiveBytes > MAX_ARCHIVE_BYTES)
                        throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                    if (requested) out.write(buffer, 0, n);
                }
                if (requested) result = out.toByteArray();
            }
        }
        return result;
    }

    private static Document parseXml(byte[] xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // Chặn XXE: không DOCTYPE, không thực thể ngoài
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        try (InputStream in = new ByteArrayInputStream(xml)) {
            return b.parse(in);
        }
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                out.add(el);
            }
        }
        return out;
    }

    private static Element first(Element parent, String localName) {
        List<Element> list = children(parent, localName);
        return list.isEmpty() ? null : list.get(0);
    }

    private static String attr(Element el, String localName) {
        if (el.hasAttribute(localName)) {
            return el.getAttribute(localName);
        }
        for (int i = 0; i < el.getAttributes().getLength(); i++) {
            Node a = el.getAttributes().item(i);
            if (localName.equals(a.getLocalName())) {
                return a.getNodeValue();
            }
        }
        return null;
    }

    private static Cell cellAt(List<Cell> row, int col) {
        return col < row.size() && row.get(col) != null ? row.get(col) : Cell.EMPTY;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
