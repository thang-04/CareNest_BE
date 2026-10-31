package com.carenest.service.eduplan.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Đọc file bảng mục tiêu (Word .docx, Excel .xlsx, Word .doc do CareNest xuất dạng HTML, CSV) thành các bảng ô đã trải
 * ô gộp (EDU-46). Ô gộp dọc lặp chữ ở các dòng dưới với cờ {@code continued} để biết là cùng mục tiêu; ô gộp ngang để
 * chữ ở ô đầu. Chỉ dùng JDK (zip + XML), tắt DTD/thực thể ngoài, giới hạn dung lượng giải nén.
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

    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final byte[] OLE_HEADER = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final Pattern HTML_ROW =
            Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_CELL =
            Pattern.compile("<(td|th)\\b([^>]*)>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TABLE =
            Pattern.compile("<table\\b[^>]*>(.*?)</table>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern XLSX_REF = Pattern.compile("([A-Z]+)(\\d+)");

    private GoalFileGrid() {}

    /** Đọc theo đuôi file và nội dung; định dạng không hỗ trợ ⇒ GOAL_FILE_UNSUPPORTED. */
    static List<Table> read(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean zip = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
        if (startsWith(bytes, OLE_HEADER)) {
            throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
        }
        try {
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

    /* ---------- Word .docx ---------- */

    private static List<Table> docx(byte[] bytes) throws IOException, SAXException, ParserConfigurationException {
        byte[] xml = zipEntry(bytes, "word/document.xml");
        if (xml == null) {
            throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
        }
        Document doc = parseXml(xml);
        List<Table> tables = new ArrayList<>();
        NodeList tbls = doc.getElementsByTagNameNS("*", "tbl");
        for (int i = 0; i < tbls.getLength(); i++) {
            Element tbl = (Element) tbls.item(i);
            // Bảng lồng trong ô: đọc riêng ở vòng sau, không trộn vào bảng ngoài
            List<List<Cell>> rows = new ArrayList<>();
            for (Element tr : children(tbl, "tr")) {
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
        }
        return new int[] {Integer.parseInt(m.group(2)), col - 1};
    }

    /* ---------- Word .doc do CareNest xuất (HTML) ---------- */

    private static List<Table> html(String text) {
        List<Table> tables = new ArrayList<>();
        Matcher tm = HTML_TABLE.matcher(text);
        while (tm.find()) {
            List<List<Cell>> rows = new ArrayList<>();
            // Ô rowspan còn phải phủ xuống các dòng sau: cột -> [số dòng còn lại, chữ]
            Map<Integer, Object[]> pending = new HashMap<>();
            Matcher rm = HTML_ROW.matcher(tm.group(1));
            while (rm.find()) {
                List<Cell> row = new ArrayList<>();
                Matcher cm = HTML_CELL.matcher(rm.group(1));
                int col = 0;
                while (true) {
                    while (pending.containsKey(col)) {
                        col = fillPending(row, pending, col);
                    }
                    if (!cm.find()) {
                        break;
                    }
                    String attrs = cm.group(2);
                    int colspan = Math.max(1, parseInt(htmlAttr(attrs, "colspan"), 1));
                    int rowspan = Math.max(1, parseInt(htmlAttr(attrs, "rowspan"), 1));
                    String cellText = htmlText(cm.group(3));
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

    private static String htmlText(String html) {
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li)>", "\n")
                .replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return s.lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
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
                row.add(new Cell(cell.toString().strip(), false));
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
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
            row.add(new Cell(cell.toString().strip(), false));
            rows.add(row);
        }
        return new Table(rows);
    }

    private static int count(String s, char ch) {
        return (int) s.chars().filter(c -> c == ch).count();
    }

    /* ---------- tiện ích ---------- */

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
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!name.equals(e.getName())) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = zin.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_ENTRY_BYTES) {
                        throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
                    }
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        }
        return null;
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
