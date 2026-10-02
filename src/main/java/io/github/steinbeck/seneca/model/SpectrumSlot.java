package io.github.steinbeck.seneca.model;

/**
 * The seven spectrum slots of a dataset (legacy {@code SenecaDataset} fields), in SpecML write order.
 *
 * <p>{@link #key} is the golden echo slot name, {@link #defaultName} the {@code NMRSpectrum.name} set by
 * the dataset constructor, {@link #specType} the SpecML {@code <type>} text that identifies the slot
 * when reading, {@link #defaultNuclei} the constructor's axis nuclei.
 */
public enum SpectrumSlot {
    CARBON1D("carbon1D", "carbon1d", "Carbon1D", "13C"),
    DEPT90("dept90", "dept90", "DEPT-90", "13C"),
    DEPT135("dept135", "dept135", "DEPT-135", "13C"),
    HETCOR("hetcor", "hetcor", "1JCH correlation", "1H", "13C"),
    HETCORLR("hetcorlr", "hetcorlr", "CH long-range correlation", "1H", "13C"),
    HHCOSY("hhcosy", "hhcosy", "H,H-COSY", "1H", "1H"),
    NOESY("noesy", "noesy", "NOESY", "1H", "1H");

    public final String key;
    public final String defaultName;
    public final String specType;
    private final String[] nuclei;

    SpectrumSlot(String key, String defaultName, String specType, String... nuclei) {
        this.key = key;
        this.defaultName = defaultName;
        this.specType = specType;
        this.nuclei = nuclei;
    }

    public String[] defaultNuclei() {
        return nuclei.clone();
    }

    /** @return the slot whose SpecML {@code <type>} text equals {@code specType}, or null */
    public static SpectrumSlot bySpecType(String specType) {
        for (SpectrumSlot s : values()) if (s.specType.equals(specType)) return s;
        return null;
    }
}
