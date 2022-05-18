java_library(
    name = "srg_training",
    srcs = ["SrgTraining.java"],
    deps = [
        "@maven//:args4j_args4j",
        "@maven//:com_google_guava_guava",
        "@maven//:com_opencsv_opencsv",
        "@maven//:org_json_json",
    ],
)

java_binary (
    name = "SrgTraining",
    main_class = "emspishak.nypd.srgtraining.SrgTraining",
    runtime_deps = [":srg_training"],
)
