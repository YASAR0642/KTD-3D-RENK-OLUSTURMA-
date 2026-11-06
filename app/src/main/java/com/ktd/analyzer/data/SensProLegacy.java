package com.ktd.analyzer.data;
/** Direct AVR firmware threshold comparisons, without unsupported metal/void labels. */
public final class SensProLegacy {
    private SensProLegacy(){}
    public static String label(int adc){
        if(adc<0||adc>1023)return "Ölçüm yok";
        if(adc<180)return "A (<180)";
        if(adc>=200&&adc<=260)return "B (200–260)";
        if(adc>=366)return "C (≥366)";
        return "Eşik dışı";
    }
}
