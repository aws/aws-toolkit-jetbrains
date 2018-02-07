package software.aws.toolkits.jetbrains.lambda.explorer

import com.intellij.util.io.HttpRequests
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement
data class Requests(@field:[XmlElement(name = "request")] val requests: MutableList<SampleRequest>? = null)

data class SampleRequest(
    @field:[XmlAttribute] val category: String? = null,
    @field:[XmlElement] val name: String? = null,
    @field:[XmlElement] val filename: String? = null
) {
    override fun toString(): String = name!!
}

//TODO: replace this with whatever caching solution we use
class ExternalResourceManager {

    val sampleRequests: List<SampleRequest> by lazy {
        val unmarshaller = JAXBContext.newInstance(Requests::class.java).createUnmarshaller()!!
        val requests = HttpRequests.request(BASE_URL + MANIFEST_FILE).connect {
            unmarshaller.unmarshal(it.inputStream) as Requests
        }!!
        requests.requests?.toList() ?: listOf()
    }

    fun getSampleRequest(fileName: String): String = HttpRequests.request(BASE_URL + fileName).readString(null).replace("\r\n", "\n")

    companion object {
        private const val BASE_URL = "https://aws-vs-toolkit.s3.amazonaws.com/LambdaSampleFunctions/SampleRequests/"
        private const val MANIFEST_FILE = "manifest.xml"
        fun getInstance(): ExternalResourceManager = ExternalResourceManager()
    }
}
