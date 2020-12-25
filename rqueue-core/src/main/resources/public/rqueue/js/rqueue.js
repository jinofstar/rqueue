/*
 * Copyright 2020 Sonu Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var queueName = null;
var dataPageUrl = null;
var dataKey = null;
var dataName = null;
var deleteActionMessage = null;
var dataType = null;
var currentPage = 0;
var defaultPageSize = null;
var deleteButtonId = null;
var urlPrefix = "";

function getAbsoluteUrl(url) {
  if (urlPrefix === undefined || urlPrefix === "") {
    return url
  }
  return urlPrefix + url
}

function showError(message) {
  $("#global-error-container").show();
  $("#global-error-message").text(message);
}

function ajaxRequest(url, type, payload, successHandler, failureHandler) {
  $.ajax({
    url: url,
    data: json(payload),
    type: type,
    dataType: 'json',
    contentType: 'application/json',
    processData: false,
    success: successHandler,
    fail: failureHandler
  });
}

function getSelectedOption(id) {
  return $('#' + id + ' :selected').val();
}

function json(data) {
  return JSON.stringify(data);
}

//====================================================
// Queue Charts
//====================================================
function drawChartElement(data, options, div_id) {
  google.charts.load('current', {'packages': ['corechart']});
  google.charts.setOnLoadCallback(function () {
    let chart = new google.visualization.LineChart(
        document.getElementById(div_id));
    let chartData = google.visualization.arrayToDataTable(data, false);
    chart.draw(chartData, options);
  });
}

function drawChart(payload, div_id) {
  ajaxRequest(getAbsoluteUrl('rqueue/api/v1/chart'), 'POST', payload,
      function (response) {
        let title = response.title;
        let hTitle = response.hTitle;
        let vTitle = response.vTitle;
        let data = response.data;
        let options = {
          title: title,
          hAxis: {title: hTitle, minValue: 0, titleTextStyle: {color: '#333'}},
          vAxis: {minValue: 0, title: vTitle}
        };
        drawChartElement(data, options, div_id);
      },
      function (response) {
        console.log('failed, ' + response);
        showError("Something wet wrong! Please reload!");
      });
}

function refreshStatsChart(chartParams, div_id) {
  let types = [];
  let aggregatorType = $('#stats-aggregator-type :selected').val();
  $.each($("input[name='data-type']:checked"), function () {
    types.push($(this).val());
  });
  chartParams["aggregationType"] = aggregatorType;
  chartParams["dateTypes"] = types;
  drawChart(chartParams, div_id);
}

function refreshLatencyChart(chartParams, div_id) {
  chartParams['aggregationType'] = $(
      '#latency-aggregator-type :selected').val();
  drawChart(chartParams, div_id);
}

//==================================================================
//  Data Exploration
//==================================================================

function exploreData() {
  let element = $(this);
  dataName = element.data('name');
  dataType = element.data('type');
  dataKey = element.data('key');
}

function displayHeader(response, displayPageNumberEl, pageSize) {
  let rows = response.rows;
  let tableHeader = $('#table-header');
  displayPageNumberEl.empty();
  $('#clear-queue').hide();
  for (let i = 0; i < response.actions.length; i++) {
    switch (response.actions[i]) {
      case 'DELETE':
        $('#clear-queue').show();
        break;
      default:
        console.log("Unknown action", response.actions[i])
    }
  }
  if (rows.length === pageSize) {
    $('#next-page-button').prop("disabled", false);
  } else {
    $('#next-page-button').prop("disabled", true);
  }
  if (currentPage > 0) {
    $('#previous-page-button').prop("disabled", false);
  } else {
    $('#previous-page-button').prop("disabled", true);
    tableHeader.empty();
    let headers = response.headers;
    for (let i = 0; i < headers.length; i++) {
      tableHeader.append("<th>" + headers[i] + "</th>");
    }
  }
}

function renderRow(row, tableBody) {
  let tds = "";
  let columns = row.columns;
  for (let j = 0; j < columns.length; j++) {
    let column = columns[j];
    if (column.type === 'DISPLAY') {
      tds += ("<td>" + column.value + "</td>");
    } else if (column.type === 'ACTION') {
      if (column.value === 'DELETE') {
        tds += ("<td><a href='#' class='delete-message-btn btn-danger'>Delete </a></td>");
      } else {
        console.log("Unknown value for action" + column.value);
      }
    }
  }
  tableBody.append("<tr>" + tds + "</tr>");
}

function displayTable(nextOrPrev) {
  let pageSize = parseInt($('#page-size').val());
  let pageNumber = currentPage;
  if (nextOrPrev === null) {
    defaultPageSize = pageSize + '';
  } else if (nextOrPrev === true) {
    pageNumber += 1;
  } else {
    pageNumber -= 1;
  }
  let data = {
    'src': queueName,
    'count': pageSize,
    'page': pageNumber,
    'type': dataType,
    'name': dataName,
    'key': dataKey
  };
  $.ajax({
    url: getAbsoluteUrl(dataPageUrl),
    data: data,
    traditional: true,
    success: function (response) {
      currentPage = pageNumber;
      let displayPageNumberEl = $('#display-page-number');
      displayHeader(response, displayPageNumberEl, pageSize);
      let tableBody = $('#table-body');
      tableBody.empty();
      let rows = response.rows;
      for (let i = 0; i < rows.length; i++) {
        renderRow(rows[i], tableBody);
      }
      displayPageNumberEl.append("Page #", pageNumber + 1);
    },
    fail: function (response) {
      console.log('failed, ' + response);
      showError("Something wet wrong! Please reload!");
    }
  });
}

function refreshPage() {
  displayTable(null);
}

function pollQueue() {
  currentPage = 0;
  refreshPage();
}

function nextPage() {
  displayTable(true);
}

function prevPage() {
  displayTable(false);
}

function updateDataType(element, callback) {
  let el = $(element);
  let key = el.val();
  let typeIdEl = $('#' + el.attr('id') + "-type");
  let url = getAbsoluteUrl('rqueue/api/v1/data-type?name=' + key);
  typeIdEl.empty();
  $.ajax({
    url: url,
    success: function (response) {
      if (response.code === 0) {
        if (response.val !== 'NONE') {
          typeIdEl.text(response.val);
        }
        if (callback !== undefined) {
          callback();
        }
      } else {
        alert("Failed:" + response.message + " Please retry!");
        console.log(response);
      }
    },
    fail: function (response) {
      console.log('failed, ' + response);
      showError("Something went wrong! Please retry!");
    }
  });
}

$('#explore-queue').on('hidden.bs.modal', function () {
  currentPage = 0;
  $('#previous-page-button').prop("disabled", true);
  $('#next-page-button').prop("disabled", true);
  $('#display-page-number').empty();
  $('#page-size').val(defaultPageSize);
});

//==============================================================
// Message Move form
//==============================================================

function enableKeyForm() {
  let dataType = $('#data-name-type').text();
  if (dataType === 'ZSET') {
    $('#data-key-form').show();
  } else {
    $('#data-key-form').hide();
  }
  dataKeyEl.val('');
  $('#view-data').data('type', dataType).data('name', dataNameEl.val());
}

function enableFormsIfRequired() {
  let dstDataType = $('#dst-data-type').text();
  let srcDataType = $('#src-data-type').text();
  if (dstDataType === 'ZSET') {
    $('#priority-controller-form').show();
  } else if (dstDataType === '' && srcDataType !== '') {
    let dstDataName = dstDataEl.val();
    if (dstDataName !== '') {
      $('#dst-data-type-input-form').show();
    }
  }
}

function disableForms() {
  $('#priority-controller-form').hide();
  $('#dst-data-type-input-form').hide();
}

function convertVal(txt) {
  if (txt === '' || txt === undefined) {
    return null;
  }
  return txt;
}

function getSourceType() {
  return convertVal($('#src-data-type').text());
}

function getDstType() {
  let type = convertVal($('#dst-data-type').text());
  if (type !== null) {
    return type;
  }
  return convertVal(dstDataTypeInputEl.val());
}

$('#move-button').on("click", function () {
  let messageCount = $('#number-of-messages').val();
  let other = {};
  let dstType = getSourceType();
  let srcType = getDstType();
  if (srcType === null) {
    alert("Source cannot be empty.");
    return;
  }
  let payload = {
    'src': srcDataEl.val(),
    'srcType': srcType,
    'dst': dstDataEl.val(),
    'dstType': dstType
  };
  if (messageCount !== '') {
    other['messageCount'] = parseInt(messageCount);
  }
  if ($('#priority-controller-form').is(":visible")) {
    let val = $('#priority-val').val();
    if (val !== '') {
      other['newScore'] = val;
    }
    let type = getSelectedOption('priority-type');
    if (type === 'REL') {
      other['fixedScore'] = false;
    } else if (type === 'ABS') {
      other['fixedScore'] = true;
    } else {
      throw type;
    }
  }
  payload['other'] = other;
  ajaxRequest(getAbsoluteUrl('rqueue/api/v1/move'), 'PUT', payload,
      function (response) {
        if (response.code === 0) {
          alert(
              "Transferred " + response.numberOfMessageTransferred
              + " messages");
        } else {
          alert(response.message);
        }
      },
      function (response) {
        console.log('failed, ' + response);
        showError("Something wet wrong! Please retry!");
      });
});

//================================================
// Delete Handler
//=================================================

function deleteModalBody() {
  if (deleteButtonId === "DELETE_ALL" && dataName !== undefined) {
    return "<strong>Are you sure?</strong> </br>You want to delete <b>"
        + deleteActionMessage
        + "</b>&nbsp;?&nbsp;</br>This process cannot be undone.";
  } else if (deleteButtonId === "DELETE_QUEUE" && queueName !== undefined) {
    return "Do you really want to delete <b>Queue: </b>" + queueName
        + "&nbsp;?&nbsp;This process cannot be undone.";
  }
  throw deleteButtonId;
}

function updateDeleteModal() {
  $('#delete-modal-body').empty().append(deleteModalBody());
  $('#delete-modal').modal('show');
}

function deleteMessage() {
  let id = $($($($(this).parent()).parent()).children()[0]).text();
  let url = getAbsoluteUrl('rqueue/api/v1/data-set/' + queueName + "/message/" + id);
  $.ajax({
    url: url,
    type: 'DELETE',
    success: function (response) {
      if (response.code === 0) {
        refreshPage();
      } else {
        alert("Failed:" + response.message + " Please retry!");
        console.log(response);
      }
    },
    fail: function (response) {
      console.log('failed, ' + response);
      showError("Something went wrong! Please reload!");
    }
  });
}

function deleteAll() {
  deleteButtonId = "DELETE_ALL";
  updateDeleteModal();
}

$('.delete-queue').on("click", function () {
  deleteButtonId = "DELETE_QUEUE";
  queueName = $(this).data('name');
  updateDeleteModal();
});

function makeQueueEmpty() {
  $.ajax({
    url: getAbsoluteUrl("rqueue/api/v1/data-set/" + queueName + "/" + dataName),
    type: 'DELETE',
    success: function (response) {
      if (response.code === 0) {
        currentPage = 0;
        refreshPage();
      } else {
        alert("Failed:" + response.message + " Please retry!");
        console.log(response);
      }
    },
    fail: function (response) {
      console.log('failed, ' + response);
      showError("Something went wrong! Please reload!");
    }
  });
}

function deleteQueue() {
  $.ajax({
    url: getAbsoluteUrl("rqueue/api/v1/queues/" + queueName),
    type: 'DELETE',
    success: function (response) {
      if (response.code === 0) {
        window.location.replace(window.location.href);
      } else {
        alert("Failed:" + response.message + " Please retry!");
        console.log(response);
      }
    },
    fail: function (response) {
      console.log('failed, ' + response);
      showError("Something went wrong! Please reload!");
    }
  })
}

$('.delete-btn').on("click", function () {
  $('#delete-modal').modal('hide');
  if (deleteButtonId === "DELETE_QUEUE") {
    deleteQueue();
  } else if (deleteButtonId === "DELETE_ALL") {
    makeQueueEmpty();
  } else {
    throw deleteButtonId;
  }
});
//=======================================================================
// Attach events to avoid CSP issue
//=======================================================================
$(document).on('click', '#previous-page-button', prevPage);
$(document).on('click', '#next-page-button', nextPage);
$(document).on('click', '#poll-queue-btn', pollQueue);
$(document).on('click', '#clear-queue', deleteAll);
$(document).on('click', '.delete-message-btn', deleteMessage);
$(document).on('click', '#view-data', exploreData);
$(document).on('click', '.data-explorer', exploreData);
