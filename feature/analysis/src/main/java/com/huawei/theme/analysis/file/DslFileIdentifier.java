package com.huawei.theme.analysis.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.intellij.openapi.vfs.VirtualFile;

import com.intellij.psi.PsiFile;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.rule.repository.RuleRepository;

public class DslFileIdentifier implements DslFileMatcher {

    private static final String XML_EXTENSION = "xml";
    private final Set<String> rootElementNames;

    public DslFileIdentifier(RuleRepository ruleRepository) {
        List<String> names = ruleRepository.getRootElementNames();
        this.rootElementNames = names.stream().collect(Collectors.toSet());
    }

    @Override
    public boolean isDslFile(VirtualFile file) {
        // 如果传入为 null，则不是 DSL 文件
        if (file == null) {
            return false;
        }
        // 先根据扩展名进行快速过滤，避免对非 XML 文件进行解析
        if (!XML_EXTENSION.equalsIgnoreCase(file.getExtension())) {
            return false;
        }
        // 通过读取文件内容并解析根元素名来判断是否为 DSL 文件
        return isDslRootElementByContent(file);
    }

    @Override
    public boolean isDslFile(PsiFile psiFile) {
        // 通过 PsiFile 判定是否为 DSL 文件，优先使用 PSI（语法层）判断
        if (psiFile == null) {
            return false;
        }
        VirtualFile virtualFile = psiFile.getVirtualFile();
        // 如果无法获取对应的 VirtualFile 或扩展名不是 xml，则直接返回 false
        if (virtualFile == null || !XML_EXTENSION.equalsIgnoreCase(virtualFile.getExtension())) {
            return false;
        }
        // 如果 PsiFile 已经被解析为 XmlFile，则直接从 PSI 获取根标签进行判断（更可靠且零 I/O）
        if (psiFile instanceof XmlFile) {
            XmlFile xmlFile = (XmlFile) psiFile;
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                return false;
            }
            // 通过根标签名称判断是否是 DSL 的根元素名集合中的一种
            return rootElementNames.contains(rootTag.getName());
        }
        // 如果无法使用 PSI（例如非标准环境），则回退到基于内容的解析判断
        return isDslRootElementByContent(virtualFile);
    }

    boolean isDslRootElementByContent(InputStream inputStream) {
        // 通过输入流解析 XML 并检查根元素名称是否属于 DSL 的根元素集合
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(inputStream);
            Element rootElement = document.getRootElement();
            if (rootElement == null) {
                return false;
            }
            return rootElementNames.contains(rootElement.getName());
        } catch (DocumentException e) {
            // 解析错误（例如非法 XML）时视为非 DSL 文件
            return false;
        }
    }

    private boolean isDslRootElementByContent(VirtualFile file) {
        try (InputStream is = file.getInputStream()) {
            return isDslRootElementByContent(is);
        } catch (IOException e) {
            return false;
        }
    }
}
