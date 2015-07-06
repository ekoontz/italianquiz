function me_onload() {
    // initialize global state,etc.
}

function profile_verb_and_tense(dom_id,verb,tense) {
    // load the info for this dom_id, which is the verb and the tense.
    // TODO: add server-side support for /me/game/:gameid
    // then we can have: var url = "/me/game/" + game_id;

    // ..until then, no game variable.
    var url = "/me";
    
    var url = url + "/" + verb + "/" + tense;
    $.ajax({
	datatype: "json",
	url: url,
	success: function(content) {
	    var jsonResponse = jQuery.parseJSON(content);
	    var median_ttcr = jsonResponse.median;
	    var mean_ttcr = jsonResponse.mean;
	    var count = jsonResponse.count;
	    var ttcr = jsonResponse.ttcr;
	    var level = ttcr_to_level(ttcr);

	    $("#" + dom_id).parent().addClass("level"+level);
	    $("#" + dom_id).replaceWith("&nbsp;");
	}
    });
}

function ttcr_to_level(ttcr) {
    if ((!ttcr) || (ttcr > 20000)) {
	return 0;
    }
    if (ttcr > 10000) {
	return 1;
    }
    if (ttcr > 5000) {
	return 2;
    }
    if (ttcr > 2500) {
	return 3;
    }
    return 4; // the best possible!
}

