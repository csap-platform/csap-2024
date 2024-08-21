
import _dom from "../utils/dom-utils.js";
import _net from "../utils/net-utils.js";



const logRotateModule = log_rotate_view();

export default logRotateModule


function log_rotate_view() {


	_dom.logHead( "Module loaded" );





	let logRotateData;

	let $logDurationFilter;

	$(document).ready(function() {
		initialize();
	});


	return {
		//
		createLogRotateTable: function(data, filterVal) {
			createLogRotateTable(data, filterVal)
		},
		//
		constructLogRotateTable: function() {
			constructLogRotateTable()
		}
	}

	function initialize() {
		

		$logDurationFilter = $("#filterTotal");

		$logDurationFilter.change(function() {
			console.log( `updatred `, logRotateData) ;
			createLogRotateTable(
				logRotateData, 
				$logDurationFilter.val() );
		});
	}


	function constructLogRotateTable() {
		$('body').css('cursor', 'wait');
		$.ajax({
			url: "api/event/logRotateReport",
			dataType: 'json',
			data: {
				appId: $('#appIdSearch').val(),
				life: $('#lifecycleSearch').val(),
				project: $('#projectSelect').val(),
				fromDate: $('#from').val(),
				toDate: $('#to').val()
			},
			async: false,
			success: function(loadJson) {
				//console.log(loadJson);
				//$("#logRotateTable").css("display","table");
				logRotateData = loadJson;
				$(".logRotateClass").show();
				createLogRotateTable(loadJson, $("#filterTotal").val());
			}
		});

		$("#logRotateTable").tablesorter({
			sortList: [[0, 0]]
			, theme: 'csapSummary'
		});
		var logRotateTableId = "#logRotateTable";
		$.tablesorter.computeColumnIndex($("tbody tr", logRotateTableId));
		$(logRotateTableId).trigger("updateAll");
		$('body').css('cursor', 'default');
	}

	function createLogRotateTable(data, filterVal) {
		var logRotateTable = $("#logRotateTable");
		$("#logRotateTable tbody").empty();
		var logRotateBody = jQuery('<tbody/>', {});
		logRotateTable.append(logRotateBody);
		for (var i = 0; i < data.length; i++) {
			if (data[i].TotalSeconds < filterVal)
				continue;
			var logRotateRow = jQuery('<tr/>', {});
			logRotateRow.append(jQuery('<td/>', {
				text: data[i]._id.serviceName
			}));
			logRotateRow.append(jQuery('<td/>', {
				text: data[i].Count
			}));
			logRotateRow.append(jQuery('<td/>', {
				text: data[i].MeanSeconds
			}));
			logRotateRow.append(jQuery('<td/>', {
				text: data[i].TotalSeconds
			}));

			logRotateBody.append(logRotateRow);
		}
	}
}
