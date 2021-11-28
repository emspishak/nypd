java_library(
    name = "profile_payroll",
    srcs = glob(["*.java"]),
    deps = [
        "@maven//:args4j_args4j",
        "@maven//:com_google_guava_guava",
        "@maven//:com_opencsv_opencsv",
    ],
)

java_binary(
    name = "ProfilePayroll",
    main_class = "emspishak.nypd.profilepayroll.ProfilePayroll",
    runtime_deps = [":profile_payroll"],
)
