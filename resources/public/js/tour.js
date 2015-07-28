// Begin configurable section.

var google_api_key = "AIzaSyD0tmoXx3oJAPITndZR4f3I5GcnV-Jram4";
var pitch = 0; // In streetview, angle with respect to the horizon.
// How much to increment the score for a correct answer 
// or decrement for a "I don't know".
var score_increment = 10; 
var useProfilePicAsMarker = true;

// End Configurable section.

// Begin global variables.
// TODO: some global variables might not be included in this section as they should be.
var question_at;

// every X milliseconds, decrement remaining time to answer this question on a tour.
var tour_question_decrement_interval = 5000;

var logging_level = INFO;

var step;
var direction;

var map;
var marker;
var current_zoom = 17;

var current_lat;
var current_long;

// Firenze is defined in cities.js.
// TODO: add tour_paths as an array of cities, rather than just one.
var tour_paths = {
    "it": {
	"IT": Firenze
    },
    "es": {
	"ES": Barcelona,
	"MX": Mexico_DF
    }
};

var iconMarker;

var answer_info = {};
var correct_answers = [];
var question_id;

// End global variables.

function get_quadrant(path,step) {
    log(DEBUG,"GET QUADRANT: " + path);
    var lat0 = path[step][0];
    var lat1 = path[step+1][0];

    var long0 = path[step][1];
    var long1 = path[step+1][1];

    var quadrant = 0;

    if ((lat1 >= lat0) && (long1 >= long0)) {
	log(INFO,"NORTHEAST: " + long1 + " => " + long0);
	quadrant = 0;
    }

    if ((lat1 >= lat0) && (long1 < long0)) {
	log(INFO,"NORTHWEST: " + long1 + " < " + long0);
	quadrant = 3;
    }

    if ((lat1 < lat0) && (long1 >= long0)) {
	log(INFO,"SOUTHEAST");
	quadrant = 1;
    }

    if ((lat1 < lat0) && (long1 < long0)) {
	log(INFO,"SOUTHWEST");
	quadrant = 2;
    }
    return quadrant;
}

function get_heading(path,position_index) {
    log(DEBUG,"GET_HEADING: " + path);
    var quadrant = get_quadrant(path,position_index);

    var lat0 = path[position_index][0];
    var long0 = path[position_index][0];

    var lat1 = path[position_index+1][0];
    var long1 = path[position_index+1][0];

    // lat1 > lat0: you are headed north.
    // lat1 < lat0: you are headed south.
//    var delta_x = Math.abs(lat0 - lat1);
    var delta_x = lat0 - lat1;

    // long1 > long0: you are headed east 
    // long1 < long0: you are headed west. 

//    var delta_y = Math.abs(long0 - long0);
    var delta_y = long0 - long0;

    var offset =  Math.abs((Math.atan2(delta_x,delta_y))) * (180/Math.PI);
    //var heading = offset + (90 * quadrant);
    var heading = (90 * quadrant) + 45;

    $("#offset").val(offset);

    return heading;
}

// TODO: remove target_language and locale: should only need game_id (which itself will refer to language and locale via server-side table relations)
function start_tour(target_language,target_locale,game_id) {

    var position_info = get_position_from_profile(target_language,target_locale);
    step = position_info.position;
    direction = position_info.direction;

    // TODO: tour_paths is a global variable, defined in it.js, es.js, and other places.
    path = tour_paths[target_language][target_locale];

    current_lat = path[step][0];
    current_long = path[step][1];
    quadrant = get_quadrant(path,step);
    heading = get_heading(path,0);

    map = L.map('map').setView([current_lat, current_long], current_zoom);

    L.tileLayer('https://{s}.tiles.mapbox.com/v3/{id}/{z}/{x}/{y}.png', {
	maxZoom: 18,
	attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, ' +
	    '<a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, ' +
	    'Imagery © <a href="http://mapbox.com">Mapbox</a>',
	id: 'examples.map-i875mjb7'
    }).addTo(map);
    
    if ((useProfilePicAsMarker == true) && ($("#profile").attr("src") != null)) {
	// TODO: move to map.js
	var profileIcon = L.icon({
	    iconUrl: $("#profile").attr("src"),
	    iconSize:     [16, 16], // size of the icon
	    shadowSize:   [30, 30], // size of the shadow
	    iconAnchor:   [7.5, 35], // point of the icon which will correspond to marker's location
	    shadowAnchor: [4, 62],  // the same for the shadow
	    popupAnchor:  [-18, -25] // point from which the popup should open relative to the iconAnchor
	});
	if (target_language == "it") {
	    marker = L.marker([current_lat, current_long]).addTo(map).bindPopup("<b>Benvenuti!</b>").openPopup();
            iconMarker = L.marker([current_lat, current_long], {icon: profileIcon}).addTo(map);
	}
	if (target_language == "es") {
	    marker = L.marker([current_lat, current_long]).addTo(map)
		.bindPopup("<b>Bienvenidos!</b>").openPopup();
	}
    } else {
	if (target_language == "it") {
	    marker = L.marker([current_lat, current_long]).addTo(map)
		.bindPopup("<b>Benvenuti!</b>").openPopup();
	}
	if (target_language == "es") {
	    marker = L.marker([current_lat, current_long]).addTo(map)
		.bindPopup("<b>Bienvenidos!</b>").openPopup();
	}
    }
    
    L.circle([current_lat, 
	      current_long], 10, {
	color: 'lightblue',
	fillColor: 'green',
	fillOpacity: 0.5
    }).addTo(map).bindPopup("start")

    for (i = 0; i < path.length; i++) {
	L.circle([path[i][0],
		  path[i][1]], 5, {
		      color: 'lightblue',
		      fillColor: 'transparent'
		  }).addTo(map).bindPopup("path point: " + i);
    }

    var popup = L.popup();
    
    // initialize streetview
    $("#streetviewiframe").attr("src","https://www.google.com/maps/embed/v1/streetview?key="+google_api_key+"&location="+current_lat+","+current_long+"&heading="+heading+"&pitch="+pitch+"&fov=35");
    
    user_keypress(target_language,target_locale);
    tour_loop(target_language,target_locale,game_id);
}

function get_position_from_profile(target_language,target_locale) {
    /* determine where user is on map based on their profile. */
    var retval;
    $.ajax({
	async: false,
	cache: false,
        dataType: "json",
        url: "/tour/" + target_language + "/" + target_locale + "/step",
        success: function (content) {
	    retval = content;
	}});
    return retval;
}

// TODO: rename; not really a loop - no for() or while().
function tour_loop(target_language,target_locale) {
    var game_id = $("#chooserselect").val();
    create_tour_question(target_language,target_locale,game_id);
    $("#gameinput").focus();
    $("#gameinput").val("");
}

function create_tour_question(target_language,target_locale,game_id) {
    $("#gameinput").css("background","white");
    $("#gameinput").css("color","black");

    // We use this function as the callback after we 
    // generate a question-and-answers pair.
    update_tour_q_and_a = function (content) {

	// Set the current time so that we can calculate time-to-correct-response in submit_user_guess().
	question_at = (new Date).getTime();

	// question is .source; answers are in .targets.
	var q_and_a = jQuery.parseJSON(content);
	var question = q_and_a.source;
	log(INFO,"Updating tour with question:" + question);
	$("#tourquestion").html(question);

	// update answers with all targets.
	$("#correctanswer").html("");

	log(DEBUG,"TARGETS ARE: " + q_and_a.targets);
	correct_answers = q_and_a.targets;

	var i=0;
	$.each(q_and_a.targets, function(index,value) {
	    log(DEBUG,"TARGET INDEX IS: " + index);
	    log(DEBUG,"TARGET VALUE IS: " + value);
	    $("#correctanswer").append("<div id='answer_"+i+"'>" + value + "</div>");
	    i++;
	});
	question_id = q_and_a.question_id;
    }

    // generate a question by calling /tour/<language>/generate-q-and-a on the server.
    $.ajax({
	cache: false,
        dataType: "html",
        url: "/tour/" + target_language + "/generate-q-and-a?game=" + game_id,
        success: update_tour_q_and_a
    });
}

function response_to_update() {
}

function decrement_remaining_tour_question_time() {
    log(DEBUG,"decrement remaining time..");
}

function longest_prefix_and_correct_answer(user_input,correct_answers) {
    log(INFO,"user input: " + user_input);
    log(INFO,"correct_answers: " + correct_answers);
    var prefix = "";
    var longest_answer = "";
    $.each(correct_answers,function(index,value) {
	// TODO: rewrite with $.each(user_input,function(key,value) {..});
	var i;
	for (i = 0; i <= user_input.length; i++) {
	    if (value.substring(0,i).toLowerCase() == user_input.substring(0,i).toLowerCase()) {
		if (i > prefix.length) {
		    prefix = value.substring(0,i);
		    longest_answer = value;
		}
	    }
	}
    });
    log(INFO,"longest correct answer: " + longest_answer);
    log(INFO,"prefix: " + prefix);
    return {"prefix":prefix,
	    "correct_answer":longest_answer};
}

function increment_map_score() {
    $("#scorevalue").html(parseInt($("#scorevalue").html()) + score_increment);
}

// TODO: convert other similar functions to take a language as a param rather 
// than in the function name.
function add_a_acute(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "á");
    update_user_input(language,locale);
    $("#gameinput").focus();
}

function add_a_grave(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "à");
    update_user_input(language,locale);
    $("#gameinput").focus();
}

function add_e_acute(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "é");
    update_user_input(language,locale);
    $("#gameinput").focus();
}
function add_e_grave(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "è");
    update_user_input(language,locale);
    $("#gameinput").focus();
}
function add_i_acute(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "í");
    update_user_input(language,locale);
    $("#gameinput").focus();
}
function add_n_tilde(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "ñ");
    update_user_input(language,locale);
    $("#gameinput").focus();
}
function add_o_grave(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "ò");
    update_user_input(language,locale);
    $("#gameinput").focus();
}
function add_u_acute(language,locale) {
    $("#gameinput").val($("#gameinput").val() + "ú");
    update_user_input(language,locale);
    $("#gameinput").focus();
}

function update_user_input(target_language,target_locale) {
    var user_input = $("#gameinput").val();

    // Find the longest common prefix of the user's guess and the set of possible answers.
    // The common prefix might be an empty string (e.g. if user_input is empty before user starts answering).
    var prefix_and_correct_answer = longest_prefix_and_correct_answer(user_input,correct_answers);
    var prefix = prefix_and_correct_answer.prefix;
    var correct_answer = prefix_and_correct_answer.correct_answer;

    var current_length = $("#gameinput").val().length;
    if (prefix.length > 0) {
	if (prefix.length == current_length) {
	    // if the keypress is a net gain, then increment the score...
	    $("#scorevalue").html(parseInt($("#scorevalue").html()) + 1);
	} else {
	    // if not, decrement the user's score, because the user's keypress was wrong.
	    $("#scorevalue").html(parseInt($("#scorevalue").html()) - 1);
	}
    }
    // update the user's input with this prefix (shared with one or more correct answer).
    $("#gameinput").val(prefix);

    log(DEBUG,"answer prefix: " + correct_answer);
    log(DEBUG,"answer length: " + correct_answer.length);

    var max_length = 0;

    var percent = (prefix.length / correct_answer.length) * 100;
    $("#userprogress").css("width",percent+"%");
	
    var guess = prefix;
    if ((prefix.length > 0) && (guess.toLowerCase() == correct_answer.toLowerCase())) {
	var time_to_correct_response = (new Date).getTime() - question_at;

	$("#gotitright").html("<h4>"+
			      $("#tourquestion").html() +
			      "</h4>"+
			      "<h2>"+
			      guess +
			      "</h2>"
			      );
	$("#gotitright").css("display","block");
	$("#gotitright").fadeOut(5000,function () {
	    log(INFO,"fadeOut() is over.");
	});

	/* user got it right - 'flash' their answer and submit the answer for them. */
	$("#gameinput").css("background","lime");

	$("#gameinput").keyup(function(event){
	    log(DEBUG,"You hit the key: " + event.keyCode);
	});

	log(INFO,"You got one right!");
	log(DEBUG,"calling update_map with target_language: " + target_language + " and target_locale:" + target_locale);
	update_map($("#tourquestion").html(), guess,target_language,target_locale);
	$("#gameinput").css("background","transparent");
	$("#gameinput").css("color","lightblue");
		
	$.ajax({
	    cache: false,
	    type: "POST",
	    data: {question: question_id,
		   answer: guess,
		   time: time_to_correct_response},
            dataType: "html",
            url: "/tour/update-question"}).done(function(content) {
		log(DEBUG,"response to /update-question: " + content);
	    });
	
	increment_map_score(); // here the 'score' is in kilometri (distance traveled)

	log(INFO,"submitting correct answer: " + correct_answer);

	// reset userprogress bar
	$("#userprogress").css("width","0");
	$("#gameinput").focus();
	// TODO: score should vary depending on the next 'leg' of the trip.
	// go to next question.
	return tour_loop(target_language,target_locale);
    }
}

// http://stackoverflow.com/questions/155188/trigger-a-button-click-with-javascript-on-the-enter-key-in-a-text-box
function user_keypress(target_language,target_locale) {
    $("#gameinput").keyup(function(event){
	log(DEBUG,"You hit the key: " + event.keyCode);
	update_user_input(target_language,target_locale);
    });
}

function update_map(question,correct_answer,target_language,target_locale) {    
    L.circle([current_lat, 
	      current_long], 10, {
	color: 'lightblue',
	fillColor: 'green',
	fillOpacity: 0.5
    }).addTo(map).bindPopup(question + " &rarr; <i>" + correct_answer + "</i><br/>" + "<tt>["+current_lat+","+current_long+"]</tt>")
    step = step + direction;

    var path = tour_paths[target_language][target_locale];
    navigate_to(step,path,true);
}

function non_lo_so(target_language,target_locale) {
    $("#correctanswer").css("display","block");
    $("#correctanswer").fadeOut(3000,function () {$("#correctanswer").css("display","none");});
    if ((direction == 1) && (step > 1)) {
	step = step - 1;
    }
    if (direction == -1) {
	step = step + 1;
    }
    var path = tour_paths[target_language][target_locale];
    navigate_to(step,path,false);
    $("#scorevalue").html(parseInt($("#scorevalue").html()) - score_increment);
    $("#gameinput").focus();
}

function navigate_to(step,path,do_encouragement) {
    log(DEBUG,"NAVIGATE TO: " + path);
    heading = get_heading(path,step);

    current_lat = path[step][0];
    current_long = path[step][1];

    get_quadrant(path,step);

    // update the background OpensStreetMaps position:
    map.panTo(path[step]);
   
    // update the marker on the background OpenStreetMaps too:
    marker.setLatLng(path[step]);
    if (do_encouragement == true) {
	var encouragement = Math.floor(Math.random()*encouragements.length);
	marker.setPopupContent("<b>" + encouragements[encouragement] + 
			       "</b> " + step + "/" + path.length);
    }
    if (iconMarker != undefined) {
	iconMarker.setLatLng(path[step]);
    }

    // update Google streetview:
    $("#streetviewiframe").attr("src","https://www.google.com/maps/embed/v1/streetview?key="+google_api_key+"&location="+current_lat+","+current_long+"&heading="+heading+"&pitch="+pitch+"&fov=35");
}
