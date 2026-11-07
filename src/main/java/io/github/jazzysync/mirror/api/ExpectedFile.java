package io.github.jazzysync.mirror.api;

/** A package file as described by a repository index. Size is -1 when the index omits it. */
public record ExpectedFile(long size, String sha256) {}
