package software.aws.toolkits.core.region

import com.amazonaws.regions.RegionUtils

/**
 * An SPI to provide regions supported by this toolkit
 */
interface ToolkitRegionProvider {
    fun regions(): Map<String, AwsRegion>
    fun defaultRegion(): AwsRegion

    fun lookupRegionById(regionId: String): AwsRegion {
        return regions()[regionId] ?: defaultRegion()
    }

    fun isServiceSupported(region: AwsRegion, serviceName: String): Boolean {
        return RegionUtils.getRegion(region.id).isServiceSupported(serviceName)
    }
}