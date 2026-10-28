package com.example.wiki_project.domain.document

import com.example.wiki_project.document.infrastructure.storage.LocalFileStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocalFileStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private fun storage() = LocalFileStorage(tempDir.toString())

    @Test
    @DisplayName("저장한 파일을 같은 key 로 다시 읽을 수 있고, 삭제하면 더 이상 조회되지 않는다")
    fun storeLoadDelete() {
        val storage = storage()
        val bytes = "사내 가이드".toByteArray()

        storage.store("documents/a.md", bytes)
        assertThat(storage.load("documents/a.md")!!.contentAsByteArray).isEqualTo(bytes)

        storage.delete("documents/a.md")
        assertThat(storage.load("documents/a.md")).isNull()
        storage.delete("documents/a.md") // 이미 없는 파일 삭제는 무시
    }

    @Test
    @DisplayName("기준 디렉터리 밖을 가리키는 key(../)는 저장을 거부하고 조회 시 null 을 반환한다")
    fun rejectPathTraversal() {
        val storage = storage()
        Files.writeString(tempDir.parent.resolve("secret.txt"), "secret")

        assertThrows<IllegalArgumentException> { storage.store("../escape.txt", "x".toByteArray()) }
        assertThat(storage.load("../secret.txt")).isNull()
    }

    @Test
    @DisplayName("S3 도입 전 더미 URL 로 저장된 문서는 파일이 없는 것으로 처리한다")
    fun legacyDummyUrlIsMissing() {
        val storage = storage()

        assertThat(storage.load("https://amazonaws.comdummy_보고서.PDF")).isNull()
        storage.delete("https://amazonaws.comdummy_보고서.PDF")
    }
}
