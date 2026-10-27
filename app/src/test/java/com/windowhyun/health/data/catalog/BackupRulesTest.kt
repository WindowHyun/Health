package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** 자동 백업 · 기기 이전 규칙에 가져온 템플릿 파일이 들어 있는지. 빠지면 새 폰에서 템플릿이 사라진다. */
class BackupRulesTest {

    private fun rules(name: String) = File("src/main/res/xml/$name").readText()

    @Test
    fun `imported templates are part of cloud backup and device transfer`() {
        val text = rules("data_extraction_rules.xml")
        val file = FileUserTemplateRepository.FILE_NAME

        val cloud = text.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = text.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertThat(cloud).contains("path=\"$file\"")
        assertThat(transfer).contains("path=\"$file\"")
    }

    @Test
    fun `imported templates are part of the older full backup too`() {
        assertThat(rules("backup_rules.xml")).contains("path=\"${FileUserTemplateRepository.FILE_NAME}\"")
    }
}
