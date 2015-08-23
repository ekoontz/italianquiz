var logging_level = INFO;

function class_onload() {
}

function validate_new_class(form_id) {
    var mandatory_items = {};
    $("#" + form_id).serializeArray().forEach(function(item) {
	if (item.value != "") {
	    mandatory_items[item.name] = true;
	}});

    if (mandatory_items['name'] == undefined) {
	alert("Please name your class.");
	return false;
    }
    if (mandatory_items['lang'] == undefined) {
	alert("Please choose a language for your class.");
	return false;
    }
    return true;
}
