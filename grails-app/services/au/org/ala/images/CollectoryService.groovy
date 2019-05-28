package au.org.ala.images

import grails.transaction.Transactional
import groovy.json.JsonSlurper

/**
 * Services to retrieve resource level metadata.
 */
@Transactional
class CollectoryService {

    def grailsApplication

    def serviceMethod() {}

    //a low rent cache of image metadata
    static _lookupCache = [:]

    /**
     * Adds the image metadata (dublin core terms) to the image
     * for the image's associated data resource definition in the collectory.
     *
     * @param image
     */
    def addMetadataForResource(image){

        //if there no resource UID, move on
        if(!image["dataResourceUid"]){
            return
        }

        //if there no collectory configured, move on
        if(!grailsApplication.config.collectory.baseURL){
            return
        }

        def metadata = getResourceLevelMetadata(image.dataResourceUid)


        if(metadata && metadata.imageMetadata) {
            //only add properties if they are blank on the source image
            metadata.imageMetadata.each { kvp ->
                if (kvp.value && !image[kvp.key]) {
                    image[kvp.key] = kvp.value
                }
            }
        }
    }

    def clearCache() {
        log.info("Clearing cache - current size: " + _lookupCache.size())
        _lookupCache.clear()
    }

    def getResourceLevelMetadata(dataResourceUid){

        def metadata = [:]

        if(!dataResourceUid){
            return metadata
        }

        //lookup the resource UID
        if(!_lookupCache.containsKey(dataResourceUid)){
            def url = grailsApplication.config.collectory.baseURL + "/ws/dataResource/" + dataResourceUid
            try {
                def js = new JsonSlurper()
                def json = js.parseText(new URL(url).text)
                if (json) {
                    metadata = json
                }
                _lookupCache.put(dataResourceUid, json)
            } catch (Exception e){
                log.warn("Unable to load metadata from ${url}")
            }
        } else {
            metadata = _lookupCache.get(dataResourceUid)
        }

        metadata
    }
}