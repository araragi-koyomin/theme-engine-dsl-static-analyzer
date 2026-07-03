package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 源码字符偏移↔(行,列)映射器。预计算每行起始偏移，二分查询。
 *
 * <p>行号1-based，列号0-based（与全库位置约定一致：ANTLR表达式节点getLine()为1-based、
 * getCharPositionInLine()为0-based；原AstBuilder亦为1-based行/0-based列）。</p>
 *
 * <p>消费方：AstBuilder将StAX的characterOffset校正到'<'后，经本类转为行/列写入AST节点。</p>
 */
public final class SourcePositionMapper {
    private final int[] lineStartOffsets;

    public SourcePositionMapper(String source) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                starts.add(i + 1);
            } else if (c == '\r') {
                starts.add(i + 1);
                if (i + 1 < source.length() && source.charAt(i + 1) == '\n') {
                    i++;
                }
            }
        }
        lineStartOffsets = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) {
            lineStartOffsets[i] = starts.get(i);
        }
    }

    /**
     * 返回偏移量对应的1-based行号。偏移越界时clamp到最后一行。
     */
    public int lineOf(int offset) {
        if (offset < 0) {
            return 1;
        }
        int idx = binarySearchGreatest(lineStartOffsets, offset);
        return idx + 1;
    }

    /**
     * 返回偏移量对应的0-based列号（= offset - 行起始偏移）。偏移越界时按末行计算。
     */
    public int colOf(int offset) {
        if (offset < 0) {
            return 0;
        }
        int idx = binarySearchGreatest(lineStartOffsets, offset);
        int lineStart = lineStartOffsets[idx];
        return offset - lineStart;
    }

    /**
     * 返回{line, col}，line为1-based，col为0-based。
     */
    public int[] lineCol(int offset) {
        return new int[]{lineOf(offset), colOf(offset)};
    }

    private static int binarySearchGreatest(int[] arr, int target) {
        int lo = 0;
        int hi = arr.length - 1;
        int ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= target) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
