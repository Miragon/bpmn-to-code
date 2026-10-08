package io.miragon.bpmn.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SourcedProcessModelTest {

    @Test
    fun `rejects files that would be generated under one name`() {
        val sources = listOf(
            SourcedProcessModel("corporate/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("default/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("bike-return.bpmn", testProcessModel(processId = "bike-return")),
        )

        assertThatThrownBy { SourcedProcessModel.requireDistinctArtifactNames(sources) { it.apiName } }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("'bike-leasing' would be generated from several BPMN files")
            .hasMessageContaining("corporate/bike-leasing.bpmn (process id 'bike-leasing'), default/bike-leasing.bpmn (process id 'bike-leasing')")
            .hasMessageContaining("variantName")
            .hasMessageNotContaining("bike-return")
    }

    @Test
    fun `accepts files that share a process id once their variant names tell them apart`() {
        val sources = listOf(
            SourcedProcessModel("default/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("corporate/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing", variantName = "corporate")),
        )

        assertThatCode { SourcedProcessModel.requireDistinctArtifactNames(sources) { it.apiName } }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects files that share a process id and a variant name`() {
        val sources = listOf(
            SourcedProcessModel("a/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing", variantName = "corporate")),
            SourcedProcessModel("b/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing", variantName = "corporate")),
        )

        assertThatThrownBy { SourcedProcessModel.requireDistinctArtifactNames(sources) { it.apiName } }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("a/bike-leasing.bpmn (process id 'bike-leasing', variantName 'corporate')")
    }

    @Test
    fun `rejects names that only collide once they are generated`() {
        val sources = listOf(
            SourcedProcessModel("dashed.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("underscored.bpmn", testProcessModel(processId = "bike_leasing")),
        )

        assertThatThrownBy { SourcedProcessModel.requireDistinctArtifactNames(sources) { testProcessModelApi(model = it).fileName() } }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("'BikeLeasingProcessApi' would be generated from several BPMN files")
            .hasMessageContaining("dashed.bpmn (process id 'bike-leasing'), underscored.bpmn (process id 'bike_leasing')")
    }

    @Test
    fun `reports every name that several files would be generated under`() {
        val sources = listOf(
            SourcedProcessModel("a/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("b/bike-leasing.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("a/bike-return.bpmn", testProcessModel(processId = "bike-return")),
            SourcedProcessModel("b/bike-return.bpmn", testProcessModel(processId = "bike-return")),
        )

        assertThatThrownBy { SourcedProcessModel.requireDistinctArtifactNames(sources) { it.apiName } }
            .hasMessageContaining("'bike-leasing'").hasMessageContaining("'bike-return'")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Corporate Fleet", "2024", "../corporate", "corporate.fleet"])
    fun `rejects a variant name that cannot become part of a name`(variantName: String) {
        val sources = listOf(SourcedProcessModel("bike-leasing.bpmn", testProcessModel(processId = "bike-leasing", variantName = variantName)))

        assertThatThrownBy { SourcedProcessModel.requireDistinctArtifactNames(sources) { it.apiName } }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("variantName '$variantName'").hasMessageContaining("bike-leasing.bpmn")
    }

    @ParameterizedTest
    @ValueSource(strings = ["corporate", "Corporate", "corporate-fleet", "corporate_fleet", "geschäftskunden", "site2"])
    fun `accepts a variant name made of letters, digits, underscores and dashes`(variantName: String) {
        val sources = listOf(SourcedProcessModel("bike-leasing.bpmn", testProcessModel(processId = "bike-leasing", variantName = variantName)))

        assertThatCode { SourcedProcessModel.requireDistinctArtifactNames(sources) { it.apiName } }.doesNotThrowAnyException()
    }

    @Test
    fun `keeps only the processes marked executable`() {
        val executable = SourcedProcessModel("bike-leasing.bpmn", testProcessModel(processId = "bike-leasing"))
        val draft = SourcedProcessModel("draft.bpmn", testProcessModel(processId = "draft").copy(isExecutable = false))

        assertThat(SourcedProcessModel.executableOnly(listOf(executable, draft))).containsExactly(executable)
    }
}
