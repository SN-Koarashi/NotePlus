package com.snkms.noteplus;

import android.content.Context;
import android.content.res.Resources;

public class GlobalStatic {
    final static String db_name = "noteplus";
    final static String tb_table = "note";
    final static String tb_table_img = "image";
    final static String tb_table_type = "type";
    final static String undefined_type_name = "未分類";
    // 將像素轉換為DP單位下的像素大小
    static public int dpToPx(int dp, Resources resources) {
        float density = resources
                .getDisplayMetrics()
                .density;
        return Math.round((float) dp * density);
    }
}
