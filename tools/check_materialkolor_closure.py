#!/usr/bin/env python3
"""materialkolor 引用闭合检查（exp.6 发版门禁）。

用法: python3 tools/check_materialkolor_closure.py <apk>
原理: 逐 dex 解析 type_ids(被引用类型) 与 class_defs(已定义类型)，
收集 Lcom/materialkolor/ 前缀的描述符，差集 = 引用但未定义的类。
差集为空才算闭合（exit 0），否则 exit 1 并打印差集。
背景: exp.4/exp.5 两次真机 NoClassDefFoundError 都是「引用在、定义不在」，
且检查手段曾把引用误当定义；本脚本严格区分两者。
"""
import struct
import sys
import zipfile

PREFIX = "Lcom/materialkolor/"


def uleb(d, off):
    r = 0
    s = 0
    while True:
        b = d[off]
        off += 1
        r |= (b & 0x7F) << s
        if not (b & 0x80):
            return r, off
        s += 7


def dex_sets(d):
    """返回 (referenced, defined) 两个描述符集合（全前缀，不过滤）。"""
    if d[:4] == b"cdex":
        raise ValueError("compact dex not supported")
    if d[:3] != b"dex":
        raise ValueError("not a dex file")
    s_size, s_off = struct.unpack_from("<II", d, 0x38)
    t_size, t_off = struct.unpack_from("<II", d, 0x40)
    c_size, c_off = struct.unpack_from("<II", d, 0x60)

    strings = []
    for i in range(s_size):
        (off,) = struct.unpack_from("<I", d, s_off + i * 4)
        _, p = uleb(d, off)  # utf16 长度（不按它切字节，MUTF-8 字节数与之不同）
        end = d.index(b"\x00", p)  # 字符串以 NUL 结尾
        strings.append(d[p:end].decode("utf-8", "replace"))

    types = []
    for i in range(t_size):
        (si,) = struct.unpack_from("<I", d, t_off + i * 4)
        types.append(strings[si])

    defined = set()
    for i in range(c_size):
        (ci,) = struct.unpack_from("<I", d, c_off + i * 32)
        defined.add(types[ci])
    return set(types), defined


def main():
    apk = sys.argv[1]
    ref_all, def_all = set(), set()
    with zipfile.ZipFile(apk) as z:
        names = sorted(n for n in z.namelist()
                       if n.startswith("classes") and n.endswith(".dex"))
        if not names:
            print("APK 内没有 dex")
            return 1
        for n in names:
            ref, defd = dex_sets(z.read(n))
            r = {x for x in ref if x.startswith(PREFIX)}
            f = {x for x in defd if x.startswith(PREFIX)}
            print(f"{n}: materialkolor referenced={len(r)} defined={len(f)}")
            ref_all |= r
            def_all |= f
    diff = sorted(ref_all - def_all)
    print(f"TOTAL: referenced={len(ref_all)} defined={len(def_all)} diff={len(diff)}")
    for x in diff:
        print("MISSING:", x)
    if diff:
        print("CLOSURE: FAIL")
        return 1
    print("CLOSURE: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
