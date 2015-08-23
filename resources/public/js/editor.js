var logging_level = INFO;

function toggle_expand(expand_this) {
    if (expand_this.style.height == "100px") {
	expand_this.style.height = "auto";
	expand_this.style.width = "auto";
	expand_this.style.overflow = "auto";
    } else {
	expand_this.style.height = "100px";
	expand_this.style.width = "200px";
	expand_this.style.overflow = "scroll";
    }
}

function edit_game_dialog(game_id) {
    $("#editgame"+game_id)[0].style.display = "block";
}

function edit_group_dialog(game_id) {
    $("#editgroup"+game_id)[0].style.display = "block";
}

function counts_per_verb_and_tense(dom_id,game_id,verb,tense) {
    // do an ajax call to: GET /game/(game_id)/(verb)/(tense)/count.
    // This server-side GET returns JSON of the count and a refine_param
    // that is used to create a link for the count.
    // Replace the spinner in $("#" + dom_id) with the count wrapped in a link
    // so the user can click and see the details for the expressions aggregated
    // into this count.
    var game_url = "/game/" + game_id;
    var url = game_url + "/" + verb + "/" + tense;
    $.ajax({
	datatype: "json",
	url: url,
	success: function(content) {
	    var jsonResponse = content;
	    var count = jsonResponse.count;
	    var refine_param = jsonResponse.refine_param;
	    var refine_url = game_url + "?refine=" + JSON.stringify(refine_param);
	    $("#" + dom_id).replaceWith($("<a href='" + refine_url + "'>" + count + "</a>"));
	    if (count == 0) {
		$("#" + dom_id).parent().addClass("zerowarning");
	    }
	}
    });
}

function checkAllVerbs(toggle_button) {
    var cbs = $("#row-verbcheckboxes").find(':input');
    for(var i=0; i < cbs.length; i++) {
	if(cbs[i].type == 'checkbox') {
	    cbs[i].checked = true;
	}
    }
    toggle_button.onclick = function() {
	uncheckAllVerbs(toggle_button);
    };
    $("#toggleverbs").html("Uncheck all verbs");

}

function uncheckAllVerbs(toggle_button) {
    var cbs = $("#row-verbcheckboxes").find(':input');
    for(var i=0; i < cbs.length; i++) {
	if(cbs[i].type == 'checkbox') {
	    cbs[i].checked = false;
	}
    }
    toggle_button.onclick = function() {
	checkAllVerbs(toggle_button);
    };
    $("#toggleverbs").html("Check all verbs");

}



