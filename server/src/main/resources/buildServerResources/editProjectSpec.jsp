<%@ page import="de.moormann.teamcity.awssm.AwsSmConstants" %>
<%@ page import="de.moormann.teamcity.awssm.server.AwsSmTestQueryController" %>
<%@ page import="de.moormann.teamcity.awssm.server.AwsSmSecretsLookupController" %>
<%@ page import="jetbrains.buildServer.controllers.parameters.ParameterContext" %>
<%@include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="context" scope="request" type="jetbrains.buildServer.controllers.parameters.ParameterEditContext"/>
<jsp:useBean id="awsConnections" scope="request"
             type="java.util.List<de.moormann.teamcity.awssm.server.AwsSecretsManagerParameter.AwsConnectionBean>"/>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<c:set var="project" value="<%=context.getAdditionalParameter(ParameterContext.PROJECT)%>"/>
<c:set var="buildType" value="<%=context.getAdditionalParameter(ParameterContext.BUILD_TYPE)%>"/>
<c:set var="projectId" value='${project == null ? "" : project.externalId}'/>
<c:set var="buildTypeId" value='${buildType == null ? "" : buildType.externalId}'/>

<bs:linkScript>
  /js/bs/testConnection.js
</bs:linkScript>

<script>
  BS.AwsSecretsManagerParametersDialog = OO.extend(BS.AbstractWebForm, {
    formElement: function () {
      const oldSpecForm = $('parameterSpecEditForm');
      if (oldSpecForm) {
        return oldSpecForm;
      } else {
        return $('editParamForm');
      }
    },

    submitTestQuery: function () {
      const that = this;
      BS.PasswordFormSaver.save(this, '<c:url value="<%=AwsSmTestQueryController.PATH%>"/>', OO.extend(BS.ErrorsAwareListener, {
        onFailedTestConnectionError: function (elem) {
          var text = "";
          if (elem.firstChild) {
            text = elem.firstChild.nodeValue;
          }
          BS.TestConnectionDialog.show(false, text, $('testQueryButton'));
        },

        onCompleteSave: function (form, responseXML) {
          const err = BS.XMLResponse.processErrors(responseXML, this, form.propertiesErrorsHandler);
          BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
          if (!err) {
            this.onSuccessfulSave(responseXML);
          }
        },

        onSuccessfulSave: function (responseXML) {
          that.enable();
          BS.TestConnectionDialog.show(true, "Parameter will resolve a value", $('testQueryButton'));
        }
      }));
      return false;
    }
  });

  BS.TestConnectionDialog.getSuccessTitle = () => 'Test successful';

  BS.TestConnectionDialog.getFailureTitle = () => 'Test failure';
</script>

<c:set var="notSelectedValue" value="<%=AwsSmConstants.ParameterSettings.CONNECTION_NOT_SELECTED_VALUE%>"/>
<c:set var="connectionIdProp" value="<%=AwsSmConstants.ParameterSettings.CONNECTION_ID%>"/>
<c:set var="queryProp" value="<%=AwsSmConstants.ParameterSettings.QUERY%>"/>
<c:set var="currentId" value="${propertiesBean.properties[connectionIdProp]}" scope="request"/>

<table class="runnerFormTable remoteParameter">
  <tr>
    <th><label for="${connectionIdProp}">AWS Connection: <l:star/></label></th>
    <td>
      <props:selectProperty id="${connectionIdProp}" name="${connectionIdProp}" className="longField">
        <props:option value="${notSelectedValue}">-- Please choose connection --</props:option>
        <c:forEach items="${awsConnections}" var="connection">
          <forms:option value="${connection.id}" selected="${currentId == connection.id}">
            <c:out value="${connection.displayName}"/> (<c:out value="${connection.id}"/>)
          </forms:option>
        </c:forEach>
      </props:selectProperty>
      <c:if test="${empty awsConnections}">
        <span class="smallNote">No AWS connections are available in this project. Add one under Project Settings | Connections.</span>
      </c:if>
      <span class="error" id="error_${connectionIdProp}"></span>
    </td>
  </tr>
  <tr>
    <th><label for="awssmSecret">Secret: <l:star/></label></th>
    <td>
      <input type="text" id="awssmSecret" class="longField" autocomplete="off" list="awssmSecretList"
             placeholder="Secret name or ARN"/>
      <datalist id="awssmSecretList"></datalist>
      <span class="smallNote" id="awssmSecretNote">
        Type to search the secrets of the connection's region. A full ARN can always be entered.
      </span>
    </td>
  </tr>
  <tr>
    <th><label for="awssmKeySelect">JSON Key:</label></th>
    <td>
      <select id="awssmKeySelect" class="longField" style="display: none"></select>
      <input type="text" id="awssmKeyText" class="longField" placeholder="Leave empty to use the whole secret string"/>
      <span class="smallNote" id="awssmKeyNote"></span>
      <%-- The query stored in the spec, composed of secret and key: <secret>[!/<key>] --%>
      <input type="hidden" name="prop:${queryProp}" id="${queryProp}" value="<c:out value='${propertiesBean.properties[queryProp]}'/>"/>
      <span class="error" id="error_${queryProp}"></span>
    </td>
  </tr>
  <props:hiddenProperty name="projectId" value="${projectId}"/>
  <props:hiddenProperty name="buildTypeId" value="${buildTypeId}"/>

  <tr>
    <td style="border-top: 0">
      <forms:submit id="testQueryButton" type="button" label="Test Query" className="testQueryButton"
                    onclick="return BS.AwsSecretsManagerParametersDialog.submitTestQuery();"/>
      <bs:dialog dialogId="testConnectionDialog" title="Test Query" closeCommand="BS.TestConnectionDialog.close();"
                 closeAttrs="showdiscardchangesmessage='false'">
        <div id="testConnectionStatus"></div>
        <div id="testConnectionDetails" class="mono"></div>
      </bs:dialog>
    </td>
  </tr>
</table>

<script>
  (function () {
    const lookupUrl = '<c:url value="<%=AwsSmSecretsLookupController.PATH%>"/>';
    const projectId = '<c:out value="${projectId}"/>';
    const separator = '<%=AwsSmConstants.KEY_SEPARATOR%>';
    const valuePrefix = '<%=AwsSmConstants.VALUE_PREFIX%>';

    const connection = document.getElementById('${connectionIdProp}');
    const query = document.getElementById('${queryProp}');
    const secretInput = document.getElementById('awssmSecret');
    const secretList = document.getElementById('awssmSecretList');
    const secretNote = document.getElementById('awssmSecretNote');
    const keySelect = document.getElementById('awssmKeySelect');
    const keyText = document.getElementById('awssmKeyText');
    const keyNote = document.getElementById('awssmKeyNote');

    let suggestTimer = null;
    let suggestRequest = 0;
    let keysRequest = 0;
    let keysLoadedFor = null;

    function lookup(params, onSuccess, onError) {
      $j.ajax({
        url: lookupUrl,
        data: Object.assign({projectId: projectId, connectionId: connection.value}, params),
        dataType: 'json',
        cache: false
      }).done(function (result) {
        if (result.error) onError(result.error); else onSuccess(result);
      }).fail(function (xhr) {
        onError('Request failed (HTTP ' + xhr.status + ')');
      });
    }

    function currentKey() {
      return keySelect.style.display === 'none' ? keyText.value.trim() : keySelect.value;
    }

    function syncQuery() {
      const secret = secretInput.value.trim();
      const key = currentKey();
      query.value = secret ? (key ? secret + separator + key : secret) : '';
    }

    function option(value, text) {
      const o = document.createElement('option');
      o.value = value;
      o.textContent = text;
      return o;
    }

    // keys == null: keys unknown, free text input
    function showKeys(keys, selected) {
      keySelect.innerHTML = '';
      if (keys === null) {
        keySelect.style.display = 'none';
        keyText.style.display = '';
        keyText.value = selected;
        return;
      }
      keySelect.appendChild(option('', '(whole secret string)'));
      keys.forEach(function (key) { keySelect.appendChild(option(key, key)); });
      if (selected && keys.indexOf(selected) < 0) {
        keySelect.appendChild(option(selected, selected + ' (not found in secret)'));
      }
      keySelect.value = selected;
      keyText.style.display = 'none';
      keySelect.style.display = '';
    }

    function loadKeys() {
      const secret = secretInput.value.trim();
      const target = connection.value + '|' + secret;
      if (!secret || target === keysLoadedFor) return;
      keysLoadedFor = target;

      const selected = currentKey();
      const request = ++keysRequest;
      keyNote.textContent = 'Loading keys…';
      lookup({lookup: 'keys', secretId: secret}, function (result) {
        if (request !== keysRequest) return;
        showKeys(result.keys, selected);
        keyNote.textContent = result.keys.length ? '' : 'The secret is not a JSON object, the whole secret string is used.';
        syncQuery();
      }, function (error) {
        if (request !== keysRequest) return;
        keysLoadedFor = null;
        showKeys(null, selected);
        keyNote.textContent = 'Keys could not be loaded: ' + error;
        syncQuery();
      });
    }

    function suggestSecrets() {
      const request = ++suggestRequest;
      lookup({lookup: 'secrets', prefix: secretInput.value.trim()}, function (result) {
        if (request !== suggestRequest) return;
        secretList.innerHTML = '';
        result.secrets.forEach(function (name) { secretList.appendChild(option(name, name)); });
      }, function (error) {
        if (request !== suggestRequest) return;
        secretNote.textContent = 'No suggestions available: ' + error + '. Enter the secret name or ARN manually.';
      });
    }

    secretInput.addEventListener('input', function () {
      syncQuery();
      clearTimeout(suggestTimer);
      suggestTimer = setTimeout(suggestSecrets, 300);
    });
    secretInput.addEventListener('focus', function () {
      if (!secretList.children.length) suggestSecrets();
    });
    secretInput.addEventListener('change', loadKeys);
    keySelect.addEventListener('change', syncQuery);
    keyText.addEventListener('input', syncQuery);
    connection.addEventListener('change', function () {
      secretList.innerHTML = '';
      keysLoadedFor = null;
      loadKeys();
    });

    // Split the stored query into secret and key
    let stored = query.value || '';
    if (stored.indexOf(valuePrefix) === 0) stored = stored.substring(valuePrefix.length);
    const index = stored.indexOf(separator);
    secretInput.value = index < 0 ? stored : stored.substring(0, index);
    showKeys(null, index < 0 ? '' : stored.substring(index + separator.length));
    if (connection.value !== '${notSelectedValue}') loadKeys();
  })();
</script>
