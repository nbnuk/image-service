<%@ page import="au.org.ala.images.MetaDataSourceType" %>
<g:if test="${source == MetaDataSourceType.UserDefined}">
    <btn class="btn btn-success" id="btnAddUserMetaData" style="margin-bottom: 5px"><i class="icon-plus icon-white"></i>&nbsp;Add item</btn>
</g:if>

<g:if test="${metaData}">
    <table class="table table-bordered table-condensed table-striped">
        <g:each in="${metaData}" var="md">
            <tr metaDataKey="${md.name}">
                <td class="property-name">${md.name}</td>
                <td class="property-value">${md.value}
                    <g:if test="${source == MetaDataSourceType.UserDefined}">
                        <button class="btn btn-small btn-danger btnDeleteMetadataItem pull-right"><i class="icon-remove icon-white"></i></button>
                    </g:if>
                </td>
            </tr>
        </g:each>
    </table>
</g:if>
<g:else>
    <div class="muted">
        No items
    </div>
</g:else>

<script>

    $("#btnAddUserMetaData").click(function(e) {
        e.preventDefault();
        var opts = {
            title:"Add Metadata",
            url: "${createLink(controller:'image', action:'addUserMetadataFragment', id:imageInstance.id)}",
            onClose: function() {
                if (refreshMetadata) {
                    refreshMetadata($("#tabUserDefined"));
                }
            }
        }
        showModal(opts);
    });

    $(".btnDeleteMetadataItem").click(function(e) {
        var metaDataKey = $(this).closest("[metaDataKey]").attr("metaDataKey");
        if (metaDataKey) {
            $.ajax("${createLink(controller:"webService", action: 'removeUserMetadataFromImage', id:imageInstance.imageIdentifier)}?key=" + metaDataKey).done(function(results) {
                if (refreshMetadata) {
                    refreshMetadata($("#tabUserDefined"));
                }
            });
        }

    });

</script>