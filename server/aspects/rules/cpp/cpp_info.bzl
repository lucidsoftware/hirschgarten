load("//aspects:utils/utils.bzl", "create_proto", "create_struct")

def extract_cpp_info(target, ctx, **kwargs):
    if CcInfo not in target:
        return None, None

    result = create_struct(
        copts = getattr(ctx.rule.attr, "copts", []),
        defines = getattr(ctx.rule.attr, "defines", []),
        link_opts = getattr(ctx.rule.attr, "linkopts", []),
        link_shared = getattr(ctx.rule.attr, "linkshared", False),
    )

    return create_proto(target, ctx, result, "cpp_target_info"), None
