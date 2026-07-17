package emaki.jiuwu.craft.corelib.web;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import emaki.jiuwu.craft.corelib.text.Texts;

final class WebSvgIconSanitizer {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "svg", "g", "path", "circle", "ellipse", "rect", "line", "polyline", "polygon", "title", "desc"
    );
    private static final Set<String> GLOBAL_ATTRS = Set.of(
            "aria-hidden", "focusable", "role", "transform", "fill", "fill-opacity", "stroke", "stroke-opacity",
            "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "opacity"
    );
    private static final Map<String, Set<String>> TAG_ATTRS = Map.of(
            "svg", Set.of("viewbox", "xmlns", "width", "height"),
            "path", Set.of("d"),
            "circle", Set.of("cx", "cy", "r"),
            "ellipse", Set.of("cx", "cy", "rx", "ry"),
            "rect", Set.of("x", "y", "width", "height", "rx", "ry"),
            "line", Set.of("x1", "x2", "y1", "y2"),
            "polyline", Set.of("points"),
            "polygon", Set.of("points")
    );

    private WebSvgIconSanitizer() {
    }

    static String sanitize(String svg) {
        if (Texts.isBlank(svg) || !svg.trim().regionMatches(true, 0, "<svg", 0, 4)) {
            return "";
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
            Element root = document.getDocumentElement();
            if (root == null || !"svg".equals(tagName(root))) {
                return "";
            }
            if (!scrubElement(root)) {
                return "";
            }
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(root), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean scrubElement(Element element) {
        String tag = tagName(element);
        if (!ALLOWED_TAGS.contains(tag)) {
            return false;
        }
        scrubAttributes(element, tag);
        for (Element child : childElements(element)) {
            if (!scrubElement(child)) {
                element.removeChild(child);
            }
        }
        for (Node child : childNodes(element)) {
            if (child.getNodeType() == Node.COMMENT_NODE || child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                element.removeChild(child);
            }
        }
        return true;
    }

    private static void scrubAttributes(Element element, String tag) {
        NamedNodeMap attributes = element.getAttributes();
        List<String> remove = new ArrayList<>();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attr = attributes.item(index);
            String name = attr.getNodeName().toLowerCase(Locale.ROOT);
            String value = attr.getNodeValue() == null ? "" : attr.getNodeValue().trim().toLowerCase(Locale.ROOT);
            if (!isAllowedAttribute(tag, name) || unsafeValue(value)) {
                remove.add(attr.getNodeName());
            }
        }
        for (String name : remove) {
            element.removeAttribute(name);
        }
    }

    private static boolean isAllowedAttribute(String tag, String name) {
        return GLOBAL_ATTRS.contains(name) || TAG_ATTRS.getOrDefault(tag, Set.of()).contains(name);
    }

    private static boolean unsafeValue(String value) {
        String compact = value.replaceAll("\\s+", "");
        return compact.contains("javascript:") || compact.contains("data:") || compact.contains("url(");
    }

    private static String tagName(Element element) {
        return element.getTagName().toLowerCase(Locale.ROOT);
    }

    private static List<Element> childElements(Element element) {
        List<Element> result = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                result.add(childElement);
            }
        }
        return result;
    }

    private static List<Node> childNodes(Element element) {
        List<Node> result = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            result.add(child);
        }
        return result;
    }
}
