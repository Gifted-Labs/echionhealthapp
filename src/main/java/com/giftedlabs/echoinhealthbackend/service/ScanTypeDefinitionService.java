package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.ScanFieldDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.ScanTypeDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ScanTypeDefinitionService {

    private static final List<String> DOPPLER_MEASUREMENT_COLUMNS = List.of("measurement", "finding");
    private static final List<String> DEFAULT_RECOMMENDATIONS = List.of(
            "Clinical correlation advised",
            "Follow-up imaging if symptoms persist",
            "Specialist review recommended");

    private final Map<ScanType, ScanTypeDefinitionResponse> definitions;

    public ScanTypeDefinitionService() {
        this.definitions = buildDefinitions();
    }

    public List<ScanTypeDefinitionResponse> getAllDefinitions() {
        return List.copyOf(definitions.values());
    }

    public ScanTypeDefinitionResponse getDefinition(ScanType scanType) {
        return definitions.get(scanType);
    }

    private Map<ScanType, ScanTypeDefinitionResponse> buildDefinitions() {
        EnumMap<ScanType, ScanTypeDefinitionResponse> map = new EnumMap<>(ScanType.class);

        register(map, ScanType.ABDOMINAL, "Abdominal", "Abdominal & Pelvic",
                List.of(section("liver", "Liver"), section("gallbladder", "Gallbladder"),
                        section("pancreas", "Pancreas"), section("spleen", "Spleen"),
                        section("kidneys", "Kidneys"), section("aorta", "Aorta")));
        register(map, ScanType.ABDOMEN_PELVIS_MALE, "Abdomen Pelvis Male", "Abdominal & Pelvic",
                List.of(section("abdomen", "Abdominal Findings"), section("bladder", "Bladder"),
                        section("prostate", "Prostate"), section("pelvis", "Pelvic Findings")));
        register(map, ScanType.ABDOMEN_PELVIS_FEMALE, "Abdomen Pelvis Female", "Abdominal & Pelvic",
                List.of(section("abdomen", "Abdominal Findings"), section("uterus", "Uterus"),
                        section("ovaries", "Ovaries"), section("adnexa", "Adnexa")));
        register(map, ScanType.PELVIC_MALE, "Pelvic Male", "Abdominal & Pelvic",
                List.of(section("bladder", "Bladder"), section("prostate", "Prostate"), section("seminal_vesicles", "Seminal Vesicles")));
        register(map, ScanType.PELVIC_FEMALE, "Pelvic Female", "Abdominal & Pelvic",
                List.of(section("uterus", "Uterus"), section("endometrium", "Endometrium"),
                        section("ovaries", "Ovaries"), section("adnexa", "Adnexa"), section("cul_de_sac", "Cul-de-sac")));

        register(map, ScanType.OBSTETRIC_EARLY, "Obstetric Early", "Obstetric",
                List.of(section("gestational_sac", "Gestational Sac"), section("embryo", "Embryo/Fetus"),
                        measurementSection("cardiac_activity", "Cardiac Activity", List.of("CRL", "FHR")),
                        section("uterus_adnexa", "Uterus and Adnexa")));
        register(map, ScanType.OBSTETRIC_LATE, "Obstetric Late", "Obstetric",
                List.of(measurementSection("biometry", "Fetal Biometry", List.of("BPD", "HC", "AC", "FL")),
                        section("placenta", "Placenta"), section("amniotic_fluid", "Amniotic Fluid"),
                        section("presentation", "Presentation"), section("fetal_anatomy", "Fetal Anatomy")));
        register(map, ScanType.OBSTETRIC_TWINS, "Obstetric Twins", "Obstetric",
                List.of(measurementSection("twin_a", "Twin A", List.of("BPD", "HC", "AC", "FL")),
                        measurementSection("twin_b", "Twin B", List.of("BPD", "HC", "AC", "FL")),
                        section("placentation", "Placentation"), section("cervix", "Cervix")));
        register(map, ScanType.ANOMALY, "Anomaly", "Obstetric",
                List.of(section("cranial", "Cranial Structures"), section("spine", "Spine"),
                        section("thorax", "Thorax"), section("abdomen", "Abdomen"), section("limbs", "Limbs")));
        register(map, ScanType.BIOPHYSICAL_PROFILE, "Biophysical Profile", "Obstetric",
                List.of(section("fetal_movement", "Fetal Movement"), section("fetal_tone", "Fetal Tone"),
                        section("fetal_breathing", "Fetal Breathing"), section("amniotic_fluid", "Amniotic Fluid")));

        register(map, ScanType.TRANSABDOMINAL_PELVIC, "Transabdominal Pelvic", "Gynecological",
                List.of(section("uterus", "Uterus"), section("endometrium", "Endometrium"),
                        section("ovaries", "Ovaries"), section("adnexa", "Adnexa"), section("bladder", "Bladder")));
        register(map, ScanType.TRANSVAGINAL, "Transvaginal", "Gynecological",
                List.of(section("uterus", "Uterus"), section("cervix", "Cervix"),
                        section("endometrium", "Endometrium"), section("ovaries", "Ovaries"), section("adnexa", "Adnexa")));

        register(map, ScanType.ECHO_ADULT, "Echo Adult", "Cardiac",
                List.of(measurementSection("chambers", "Cardiac Chambers", List.of("LA", "LVEDD", "LVESD", "EF")),
                        section("valves", "Valves"), section("pericardium", "Pericardium")));
        register(map, ScanType.ECHO_PEDIATRIC, "Echo Pediatric", "Cardiac",
                List.of(measurementSection("chambers", "Cardiac Chambers", List.of("LA", "RV", "LV", "EF")),
                        section("septae", "Septae"), section("great_vessels", "Great Vessels")));

        register(map, ScanType.MUSCULOSKELETAL, "Musculoskeletal", "Specialized",
                List.of(section("tendons", "Tendons"), section("muscles", "Muscles"),
                        section("joints", "Joints"), section("soft_tissues", "Soft Tissues")));
        register(map, ScanType.NECK, "Neck", "Specialized",
                List.of(section("soft_tissues", "Soft Tissues"), section("lymph_nodes", "Lymph Nodes"),
                        section("salivary_glands", "Salivary Glands"), section("vascular", "Vascular Structures")));
        register(map, ScanType.THYROID, "Thyroid", "Specialized",
                List.of(section("right_lobe", "Right Lobe"), section("left_lobe", "Left Lobe"),
                        section("isthmus", "Isthmus"), section("cervical_nodes", "Cervical Nodes")));
        register(map, ScanType.BREAST, "Breast", "Specialized",
                List.of(section("right_breast", "Right Breast"), section("left_breast", "Left Breast"),
                        section("axillae", "Axillae")));
        register(map, ScanType.CHEST, "Chest", "Specialized",
                List.of(section("pleural_space", "Pleural Space"), section("lung_bases", "Lung Bases"),
                        section("chest_wall", "Chest Wall")));
        register(map, ScanType.SCROTAL, "Scrotal", "Specialized",
                List.of(section("right_testis", "Right Testis"), section("left_testis", "Left Testis"),
                        section("epididymis", "Epididymis"), section("cords", "Spermatic Cords")));
        register(map, ScanType.PENILE, "Penile", "Specialized",
                List.of(section("corpora", "Corpora"), section("vascularity", "Vascularity"),
                        section("plaques", "Plaques")));
        register(map, ScanType.NEONATAL_HEAD, "Neonatal Head", "Specialized",
                List.of(section("ventricles", "Ventricles"), section("parenchyma", "Parenchyma"),
                        section("midline", "Midline Structures"), section("posterior_fossa", "Posterior Fossa")));

        registerDoppler(map, ScanType.ARTERIAL_DOPPLER_BOTH_LOWER, "Arterial Doppler Both Lower");
        registerDoppler(map, ScanType.ARTERIAL_DOPPLER_LEFT_LOWER, "Arterial Doppler Left Lower");
        registerDoppler(map, ScanType.ARTERIAL_DOPPLER_RIGHT_LOWER, "Arterial Doppler Right Lower");
        registerDoppler(map, ScanType.ARTERIAL_DOPPLER_LEFT_UPPER, "Arterial Doppler Left Upper");
        registerDoppler(map, ScanType.ARTERIAL_DOPPLER_RIGHT_UPPER, "Arterial Doppler Right Upper");
        registerDoppler(map, ScanType.VENOUS_DOPPLER_BOTH_LOWER, "Venous Doppler Both Lower");
        registerDoppler(map, ScanType.VENOUS_DOPPLER_LEFT_LOWER, "Venous Doppler Left Lower");
        registerDoppler(map, ScanType.VENOUS_DOPPLER_RIGHT_LOWER, "Venous Doppler Right Lower");
        registerDoppler(map, ScanType.VENOUS_DOPPLER_LEFT_UPPER, "Venous Doppler Left Upper");
        registerDoppler(map, ScanType.VENOUS_DOPPLER_RIGHT_UPPER, "Venous Doppler Right Upper");

        map.put(ScanType.GENERAL, ScanTypeDefinitionResponse.builder()
                .scanType(ScanType.GENERAL)
                .displayName("General Report")
                .category("General")
                .sections(List.of(section("general_findings", "General Findings")))
                .recommendationOptions(DEFAULT_RECOMMENDATIONS)
                .measurementColumns(List.of())
                .hasMeasurementTable(false)
                .freeFormTemplate(true)
                .build());

        return map;
    }

    private void register(Map<ScanType, ScanTypeDefinitionResponse> map, ScanType scanType, String displayName,
            String category, List<ScanFieldDefinitionResponse> sections) {
        map.put(scanType, ScanTypeDefinitionResponse.builder()
                .scanType(scanType)
                .displayName(displayName)
                .category(category)
                .sections(sections)
                .recommendationOptions(DEFAULT_RECOMMENDATIONS)
                .measurementColumns(List.of())
                .hasMeasurementTable(false)
                .freeFormTemplate(false)
                .build());
    }

    private void registerDoppler(Map<ScanType, ScanTypeDefinitionResponse> map, ScanType scanType, String displayName) {
        map.put(scanType, ScanTypeDefinitionResponse.builder()
                .scanType(scanType)
                .displayName(displayName)
                .category("Vascular/Doppler")
                .sections(List.of(
                        measurementSection("arterial_segments", "Measured Segments", List.of("PSV", "EDV", "RI")),
                        section("waveform", "Waveform Summary"),
                        section("impression", "Vascular Impression")))
                .recommendationOptions(DEFAULT_RECOMMENDATIONS)
                .measurementColumns(DOPPLER_MEASUREMENT_COLUMNS)
                .hasMeasurementTable(true)
                .freeFormTemplate(false)
                .build());
    }

    private ScanFieldDefinitionResponse section(String key, String label) {
        return ScanFieldDefinitionResponse.builder()
                .key(key)
                .label(label)
                .fieldType("TEXT")
                .required(false)
                .allowsMeasurements(false)
                .measurementLabels(List.of())
                .build();
    }

    private ScanFieldDefinitionResponse measurementSection(String key, String label, List<String> measurementLabels) {
        return ScanFieldDefinitionResponse.builder()
                .key(key)
                .label(label)
                .fieldType("TEXT_WITH_MEASUREMENTS")
                .required(false)
                .allowsMeasurements(true)
                .measurementLabels(measurementLabels)
                .build();
    }
}
