load("//aspects:utils/utils.bzl", "file_location", "is_external", "map", "update_sync_output_groups")
load("@rules_scala_annex//rules:providers.bzl", "ScalaConfiguration")

def find_scalac_classpath(runfiles):
    result = []
    found_scala_compiler_jar = False
    for file in runfiles:
        name = file.basename
        if file.extension == "jar" and ("scala3-compiler" in name or "scala-compiler" in name):
            found_scala_compiler_jar = True
            result.append(file)
        elif file.extension == "jar" and ("scala3-library" in name or "scala3-reflect" in name or "scala-library" in name or "scala-reflect" in name):
            result.append(file)
    return result if found_scala_compiler_jar and len(result) >= 2 else []

def extract_scala_info(target, ctx, output_groups, **kwargs):
    kind = ctx.rule.kind

    if not kind.startswith("scala_") and not kind.startswith("thrift_"):
        return None, None

    scala_info = {}

    if hasattr(ctx.rule.attr, "scala"):
        scala_configuration = ctx.rule.attr.scala[ScalaConfiguration]
        common_scalac_options = scala_configuration.global_scalacopts

        classpath_files = []

        for target in scala_configuration.compiler_classpath:
            for file in target[JavaInfo].runtime_output_jars:
                classpath_files.append(file)

        compiler_classpath = find_scalac_classpath(classpath_files)

        if len(compiler_classpath) > 0:
            scala_info["compiler_classpath"] = map(file_location, compiler_classpath)

            if any([is_external(target) for target in scala_configuration.compiler_classpath]):
                update_sync_output_groups(
                    output_groups,
                    "external-deps-resolve",
                    depset(compiler_classpath)
                )
    else:
        common_scalac_options = []

    scala_info["scalac_opts"] = common_scalac_options + getattr(ctx.rule.attr, "scalacopts", [])

    return dict(scala_target_info = struct(**scala_info)), None
