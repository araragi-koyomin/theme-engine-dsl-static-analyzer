package com.huawei.theme.analysis.core.fileidentification;

import java.util.Set;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

/**
 * DslFileMatcher的Core层实现，基于双重识别策略判定文件是否为DSL文件。
 *
 * <p>双重识别步骤：
 * <ol>
 *   <li>检查filePath扩展名是否为.xml（大小写不敏感）</li>
 *   <li>从content中提取根元素标签名，与M2 RuleRepository.getRootElementNames()集合匹配</li>
 * </ol>
 *
 * <p>仅做根元素名称匹配，不做完整XML解析，保证响应速度。
 * 这使得畸形XML（未闭合标签、引号缺失）只要根标签匹配即可被识别为DSL文件，
 * 交由后续M3语法分析模块报告具体语法错误。</p>
 *
 * <p>Core层不依赖IDEA SDK，使用纯字符串参数。Plugin层通过PsiDslFileMatcherAdapter
 * 将VirtualFile/PsiFile适配为String参数后调用此实现。</p>
 */
public class DslFileIdentifier implements DslFileMatcher {
    private static final String XML_EXTENSION = ".xml";

    private final Set<String> rootElementNames;

    /**
     * 构造DslFileIdentifier，从RuleRepository获取合法根元素名称集合并缓存。
     *
     * @param ruleRepository M2规则库查询接口，提供getRootElementNames()数据源
     */
    public DslFileIdentifier(RuleRepository ruleRepository) {
        this.rootElementNames = Set.copyOf(ruleRepository.getRootElementNames());
    }

    @Override
    public boolean isDslFile(String filePath, String content) {
        if (filePath == null || !filePath.toLowerCase().endsWith(XML_EXTENSION)) {
            return false;
        }
        if (content == null || content.isEmpty()) {
            return false;
        }
        String rootTag = extractRootTagName(content);
        if (rootTag == null) {
            return false;
        }
        return rootElementNames.contains(rootTag);
    }

    /**
     * 从XML内容中提取第一个真实元素标签名。
     *
     * <p>轻量扫描，跳过XML声明({@code <?xml ... ?>})和注释({@code <!-- ... -->})，
     * 返回第一个以字母或下划线开头的标签名。不依赖完整XML解析器，
     * 因此畸形XML也能正确提取根标签名。</p>
     *
     * @param content XML文件内容
     * @return 根元素标签名，无法提取时返回null
     */
    private String extractRootTagName(String content) {
        int i = 0;
        int len = content.length();
        while (i < len) {
            int lt = content.indexOf('<', i);
            if (lt < 0 || lt + 1 >= len) {
                break;
            }
            char next = content.charAt(lt + 1);
            if (next == '?') {
                int end = content.indexOf("?>", lt);
                if (end < 0) {
                    break;
                }
                i = end + 2;
            } else if (next == '!') {
                int end = content.indexOf("-->", lt);
                if (end < 0) {
                    break;
                }
                i = end + 3;
            } else if (isNameStartChar(next)) {
                int start = lt + 1;
                int end = start + 1;
                while (end < len && isNameChar(content.charAt(end))) {
                    end++;
                }
                return content.substring(start, end);
            } else {
                i = lt + 1;
            }
        }
        return null;
    }

    private static boolean isNameStartChar(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }
}
