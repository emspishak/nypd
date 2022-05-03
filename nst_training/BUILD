java_library(
    name = "nst_training",
    srcs = ["NstTraining.java"],
    deps = [
        "@maven//:args4j_args4j",
        "@maven//:com_google_guava_guava",
        "@maven//:com_opencsv_opencsv",
        "@maven//:org_json_json",
    ],
)

java_binary (
    name = "NstTraining",
    main_class = "emspishak.nypd.nsttraining.NstTraining",
    runtime_deps = [":nst_training"],
)
