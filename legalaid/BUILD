load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary")

java_library(
    name = "legal_aid",
    srcs = ["LegalAid.java"],
    deps = [
        "@maven//:args4j_args4j",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_http_client_google_http_client",
        "@maven//:org_json_json",
    ],
)

java_binary(
    name = "LegalAid",
    main_class = "emspishak.nypd.legalaid.LegalAid",
    runtime_deps = [":legal_aid"],
)

nodejs_binary(
    name = "ExistingToJson",
    entry_point = "existing-to-json.js",
)
