package com.example.wiki_project.document.infrastructure.storage

import com.example.wiki_project.document.application.FileStorage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 로컬 디스크 파일 저장소 (S3 도입 전 임시 구현)
 * - 운영: docker 볼륨(/data/uploads)에 저장하여 컨테이너 재배포 후에도 유지
 * - key 가 기준 디렉터리 밖을 가리키면(../ 등) 거부하여 경로 조작 차단
 */
@Component
class LocalFileStorage(
    @Value("\${storage.local.base-dir:./data/uploads}") baseDir: String
) : FileStorage {

    private val root: Path = Paths.get(baseDir).toAbsolutePath().normalize()

    override fun store(key: String, bytes: ByteArray) {
        val path = resolve(key) ?: throw IllegalArgumentException("잘못된 저장소 경로입니다: $key")
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
    }

    override fun load(key: String): Resource? {
        val path = resolve(key) ?: return null
        return if (Files.isRegularFile(path)) FileSystemResource(path) else null
    }

    override fun delete(key: String) {
        resolve(key)?.let { Files.deleteIfExists(it) }
    }

    private fun resolve(key: String): Path? {
        val path = try {
            root.resolve(key).normalize()
        } catch (e: InvalidPathException) {
            return null
        }
        return if (path.startsWith(root) && path != root) path else null
    }
}
