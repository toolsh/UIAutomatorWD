package com.macaca.android.testing.server.xmlUtils;

/**
 * Created by xdf on 09/05/2017.
 */

import android.os.SystemClock;
import android.support.test.uiautomator.UiDevice;
import android.view.accessibility.AccessibilityNodeInfo;

import com.macaca.android.testing.server.common.Elements;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

/**
 * Find matching UiElement by XPath.
 */
public class XPathSelector {
    private static final XPath XPATH_COMPILER = XPathFactory.newInstance().newXPath();
    // document needs to be static so that when buildDomNode is called recursively
    // on children they are in the same document to be appended.
    private static Document document;
    // The two maps should be kept in sync
    private static final Map<UiElement<?, ?>, Element> TO_DOM_MAP =
            new HashMap<UiElement<?, ?>, Element>();
    private static final Map<Element, UiElement<?, ?>> FROM_DOM_MAP =
            new HashMap<Element, UiElement<?, ?>>();
    private static UiDevice uiDevice = Elements.getGlobal().getmDevice();

    public static void clearData() {
        TO_DOM_MAP.clear();
        FROM_DOM_MAP.clear();
        document = null;
    }

    private final String xPathString;
    private XPathExpression xPathExpression;
    private static UiAutomationElement rootElement;

    public XPathSelector(String xPathString) throws Exception {
        this.xPathString = xPathString;
        xPathExpression = XPATH_COMPILER.compile(xPathString);
    }

    public NodeInfoList find(UiElement context) throws Exception {
        Element domNode = getDomNode((UiElement<?, ?>) context);
        NodeInfoList list = new NodeInfoList();
        getDocument().appendChild(domNode);
        NodeList nodes = (NodeList) xPathExpression.evaluate(domNode, XPathConstants.NODESET);
        int nodesLength = nodes.getLength();
        for (int i = 0; i < nodesLength; i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE && !FROM_DOM_MAP.get(nodes.item(i)).getClassName().equals("hierarchy")) {
                list.addToList(FROM_DOM_MAP.get(nodes.item(i)).node);
            }
        }
        try {
            getDocument().removeChild(domNode);
        } catch (DOMException e) {
            document = null;
        }
        return list;
    }

    public static NodeInfoList getNodesList(String xpathExpression) throws Exception {
        XPathSelector.refreshUiElementTree();
        XPathSelector finder = new XPathSelector(xpathExpression);
        return finder.find(finder.getRootElement());
    }

    private static Document getDocument() throws Exception {
        if (document == null) {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        }
        return document;
    }

    /**
     * Returns the DOM node representing this UiElement.
     */
    private static Element getDomNode(UiElement<?, ?> uiElement) throws Exception {
        Element domNode = TO_DOM_MAP.get(uiElement);
        if (domNode == null) {
            domNode = buildDomNode(uiElement);
        }
        return domNode;
    }

    private static void setNodeLocalName(Element element, String className) throws Exception {
        Field localName = element.getClass().getDeclaredField("localName");
        localName.setAccessible(true);
        localName.set(element, tag(className));
    }

    private static Element buildDomNode(UiElement<?, ?> uiElement) throws Exception {
        String className = uiElement.getClassName();
        if (className == null) {
            className = "UNKNOWN";
        }
        Element element = getDocument().createElement(simpleClassName(className));
        TO_DOM_MAP.put(uiElement, element);
        FROM_DOM_MAP.put(element, uiElement);

        setNodeLocalName(element, className);

        setAttribute(element, Attribute.INDEX, String.valueOf(uiElement.getIndex()));
        setAttribute(element, Attribute.CLASS, className);
        setAttribute(element, Attribute.RESOURCE_ID, uiElement.getResourceId());
        setAttribute(element, Attribute.PACKAGE, uiElement.getPackageName());
        setAttribute(element, Attribute.CONTENT_DESC, uiElement.getContentDescription());
        setAttribute(element, Attribute.TEXT, uiElement.getText());
        setAttribute(element, Attribute.CHECKABLE, uiElement.isCheckable());
        setAttribute(element, Attribute.CHECKED, uiElement.isChecked());
        setAttribute(element, Attribute.CLICKABLE, uiElement.isClickable());
        setAttribute(element, Attribute.ENABLED, uiElement.isEnabled());
        setAttribute(element, Attribute.FOCUSABLE, uiElement.isFocusable());
        setAttribute(element, Attribute.FOCUSED, uiElement.isFocused());
        setAttribute(element, Attribute.SCROLLABLE, uiElement.isScrollable());
        setAttribute(element, Attribute.LONG_CLICKABLE, uiElement.isLongClickable());
        setAttribute(element, Attribute.PASSWORD, uiElement.isPassword());
        if (uiElement.hasSelection()) {
            element.setAttribute(Attribute.SELECTION_START.getName(),
                    Integer.toString(uiElement.getSelectionStart()));
            element.setAttribute(Attribute.SELECTION_END.getName(),
                    Integer.toString(uiElement.getSelectionEnd()));
        }
        setAttribute(element, Attribute.SELECTED, uiElement.isSelected());
        element.setAttribute(Attribute.BOUNDS.getName(), uiElement.getBounds() == null ? null : uiElement.getBounds().toShortString());

        for (UiElement<?, ?> child : uiElement.getChildren()) {
            element.appendChild(getDomNode(child));
        }
        return element;
    }

    private static void setAttribute(Element element, Attribute attr, String value) {
        if (value != null) {
            element.setAttribute(attr.getName(), value);
        }
    }

    private static void setAttribute(Element element, Attribute attr, boolean value) {
        element.setAttribute(attr.getName(), String.valueOf(value));
    }

    public UiAutomationElement getRootElement() throws Exception {
        if (rootElement == null) {
            refreshUiElementTree();
        }
        return rootElement;
    }

    public static void refreshUiElementTree() throws Exception {
        rootElement = UiAutomationElement.newRootElement(getRootAccessibilityNode());
    }


    public static AccessibilityNodeInfo getRootAccessibilityNode() throws Exception {
        final long timeoutMillis = 10000;
        uiDevice.waitForIdle(timeoutMillis);
        long end = SystemClock.uptimeMillis() + timeoutMillis;
        while (true) {
            AccessibilityNodeInfo root = UiAutomatorBridge.getInstance().getQueryController().getAccessibilityRootNode();
            if (root != null) {
                return root;
            }
            long remainingMillis = end - SystemClock.uptimeMillis();
            SystemClock.sleep(Math.min(250, remainingMillis));
        }
    }

    /**
     * @return The tag name used to build UiElement DOM. It is preferable to use
     * this to build XPath instead of String literals.
     */
    public static String tag(String className) {
        // the nth anonymous class has a class name ending in "Outer$n"
        // and local inner classes have names ending in "Outer.$1Inner"
        className = className.replaceAll("\\$[0-9]+", "\\$");
        return className;
    }

    /**
     * returns by excluding inner class name.
     */
    private static String simpleClassName(String name) {
        name = name.replaceAll("\\$[0-9]+", "\\$");
        int start = name.lastIndexOf('$');
        if (start == -1) {
            return name;
        }
        return name.substring(0, start);
    }
}

