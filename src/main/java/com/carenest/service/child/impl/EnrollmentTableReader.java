package com.carenest.service.child.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;

final class EnrollmentTableReader {
    private static final int MAX_ROWS = 501;
    private static final int MAX_COLUMNS = 128;

    private EnrollmentTableReader() {}

    static List<List<String>> read(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException();
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv"))
            return csv(new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", ""));
        if (name.endsWith(".xls")) return xls(file);
        if (name.endsWith(".xlsx")) return xlsx(file);
        throw new IllegalArgumentException();
    }

    private static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (!quoted && (c == ',' || c == '\n' || c == '\r')) {
                row.add(cell.toString());
                cell.setLength(0);
                if (row.size() > MAX_COLUMNS) throw new IllegalArgumentException();
                if (c != ',') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    append(rows, row);
                    row = new ArrayList<>();
                }
            } else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException();
        row.add(cell.toString());
        append(rows, row);
        return rows;
    }

    private static void append(List<List<String>> rows, List<String> row) {
        if (row.stream().allMatch(String::isBlank)) return;
        if (row.size() > MAX_COLUMNS || rows.size() >= MAX_ROWS) throw new IllegalArgumentException();
        rows.add(row);
    }

    private static List<List<String>> xls(MultipartFile file) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException();
            var sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() >= MAX_ROWS) throw new IllegalArgumentException();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<List<String>> rows = new ArrayList<>();
            for (var row : sheet) {
                if (row.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException();
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    var cell = row.getCell(i);
                    if (cell != null && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        cells.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                    } else cells.add(formatter.formatCellValue(cell));
                }
                append(rows, cells);
            }
            return rows;
        }
    }

    private static Document xml(byte[] bytes) throws Exception {
        if (bytes == null) throw new IllegalArgumentException();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private static List<List<String>> xlsx(MultipartFile file) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entries.size() >= 512) throw new IllegalArgumentException();
                byte[] data = zip.readNBytes(32 * 1024 * 1024 - total + 1);
                total += data.length;
                if (total > 32 * 1024 * 1024 || entries.putIfAbsent(entry.getName(), data) != null)
                    throw new IllegalArgumentException();
            }
        }
        List<String> strings = new ArrayList<>();
        if (entries.containsKey("xl/sharedStrings.xml")) {
            NodeList items = xml(entries.get("xl/sharedStrings.xml")).getElementsByTagName("si");
            for (int i = 0; i < items.getLength(); i++)
                strings.add(items.item(i).getTextContent());
        }
        Document workbook = xml(entries.get("xl/workbook.xml"));
        Element sheet = (Element) workbook.getElementsByTagName("sheet").item(0);
        if (sheet == null) throw new IllegalArgumentException();
        String relation = sheet.getAttribute("r:id");
        NodeList relationships = xml(entries.get("xl/_rels/workbook.xml.rels")).getElementsByTagName("Relationship");
        String target = null;
        for (int i = 0; i < relationships.getLength(); i++) {
            Element rel = (Element) relationships.item(i);
            if (relation.equals(rel.getAttribute("Id")) && !"External".equals(rel.getAttribute("TargetMode")))
                target = rel.getAttribute("Target");
        }
        if (target == null || target.contains("..")) throw new IllegalArgumentException();
        String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        NodeList sheetRows = xml(entries.get(path)).getElementsByTagName("row");
        if (sheetRows.getLength() > MAX_ROWS) throw new IllegalArgumentException();
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < sheetRows.getLength(); i++) {
            NodeList cells = ((Element) sheetRows.item(i)).getElementsByTagName("c");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < cells.getLength(); j++) {
                Element cell = (Element) cells.item(j);
                String reference = cell.getAttribute("r");
                int column = 0;
                for (char c : reference.toCharArray()) {
                    if (c < 'A' || c > 'Z') break;
                    column = column * 26 + c - 'A' + 1;
                }
                if (column < 1 || column > MAX_COLUMNS) throw new IllegalArgumentException();
                while (values.size() < column) values.add("");
                String type = cell.getAttribute("t");
                NodeList nodes = cell.getElementsByTagName("v");
                String value = nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
                if ("s".equals(type) && !value.isEmpty()) value = strings.get(Integer.parseInt(value));
                if ("inlineStr".equals(type)) value = cell.getTextContent();
                values.set(column - 1, value);
            }
            append(rows, values);
        }
        return rows;
    }
}
