var staticContentRow = 1;

function got(here) {
    $("#guessbody").prepend("<tr style='display:none' id='got_row_" + staticContentRow + "'><th>"+staticContentRow+"</th><th>got</th><td>" + here + "</td></tr>");
    $("#got_row_"+staticContentRow).fadeIn("slow");
    staticContentRow++;
}

var guessNumber = 1;

function addguess(english,italian) {
    $("#guess-table").prepend("<tr style='display:none' id='guess_row_" + guessNumber + "'><th>" + guessNumber + "</th><th>" + english + "</th><td>" + italian + "</td></tr>");
    $("#guess_row_"+guessNumber).fadeIn("slow");
    guessNumber++;
}

function fade_in(row_id) {
    $("#tr_"+row_id).fadeIn("fast");
}

function submit_user_response(form_input_id) {
    var guess = $("#"+form_input_id).val();

    // 1. apply user's guess to guess evaluation.
    $.ajax({
        dataType: "html",
        data: {guess: guess, qid: $("#question_id").val()},
        type: "POST",
        contentType: "application/x-www-form-urlencoded;charset=utf-8",
        url: "/italian/quiz/evaluate/",
        success: function (content) {
            $("#quiz_table").prepend(content);
        }
    });
    // 2. generate a new question and present it to the user.
    get_next_question();
    // 3. initialize UI so that user is ready to answer question.
    clear_guess_input();
}

function get_next_question() {
    $.ajax({
        dataType: "html",
        url: "/italian/quiz/question/",
        success: function (content) {
            $("#ajax_question").html(content);
        }
    });
}

function clear_guess_input() {
    $("#guess_input").val('');
    $("#guess_input").focus();
}

function remove_pluses(string) {
    var re = /\+/g;
    var newstr = string.replace(re, " ");
    return newstr;
}

function ajax_quiz() {
    clear_guess_input();

    // show quiz controls form.
    $.ajax({
        dataType: "html",
        url: "/italian/quiz/filter/",
        success: function (content) {
            $("#controls_container").html(content);
        }
    });
}

function submit_quiz_filters(container, form) {
    $.ajax({
        dataType: "html",
        data: $(form).serialize(),
        type: "POST",
        contentType: "application/x-www-form-urlencoded;charset=utf-8",
        url: "/italian/quiz/filter/",
        success: function (content) {
            $(container).html(content);
        }
    });
}

function table_row(question_id, perfect) {
    var english =  $("#"+question_id+"_en").html();
    var italian =  $("#"+question_id+"_it").html();
    var rowspan = "1";
    var row_id = "tr_"+question_id+"_js"; // <-"_js" will go away.
    if (perfect == true) {rowspan = 1;} else {rowspan = 2;}
    var english_td = "<td class='en' rowspan='" + rowspan + "'>" + english + "</td>";
    var evaluation = $("#"+row_id+"_eval").html();
    correct_td = "";
    if (perfect == true) {
        correct_td = "<td class='corr'> " + evaluation + "</td>";
    } else {
        correct_td = "<td>" + italian + "</td>";
    }
    var stripe = $("#stripe_toggle").html();

    var eval_tr = "";
    if (perfect != true) {
        eval_tr = "<tr class='" + stripe + "'><td class='incorr'>" + evaluation + "</td></tr>";
    } else {
        eval_tr = ""; // no correction necessary: user's response was correct.
    }

    var row
        = "<tbody id='" + row_id + "' style='display:none'   >" +
          "  <tr class='" + stripe + "'>" +
        english_td +
        correct_td +
          "</tr>" +
          eval_tr +
        "</tbody>";

    $("#quiz_table").prepend(row);

    $("#" + row_id ).fadeIn("fast");

    if (stripe == "odd") {
        $("#stripe_toggle").html("even");
    } else {
        $("#stripe_toggle").html("odd");
    }
}
