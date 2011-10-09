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
        contentType: "application/x-www-form-urlencoded;charset=ISO-8859-1",
        url: "/evaluate/tr/",
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
        url: "/guess/question/",
        success: function (content) {
            $("#ajax_question").html(content);
        }
    });
}

function clear_guess_input() {
    $("#guess_input").val('');
    $("#guess_input").focus();
}

function ajax_quiz() {
    document.body.innerHTML = 
        "<div id='ajax_quiz'>" +
        "  <div id='ajax_question'><script>get_next_question();</script></div>" +
        "  <input size='100' id='guess_input' type='text'><script>clear_guess_input();</script></input>" +
        "  <button class='click' onclick='submit_user_response(\"guess_input\")'>Rispondi</button>" +
        "  <table id='quiz_table'></table>" +
        "</div>";
    get_next_question();
    clear_guess_input();
}

function table_row(question_id, english, italian, perfect, formatted_evaluation) {
    var rowspan = "1";
    var row_id = "tr_"+question_id+"_js"; // <-"_js" will go away.
    if (perfect == "true") {rowspan = 1;} else {rowspan = 2;}
    var english_td = "<td rowspan='" + rowspan + "'>" + english + "</td>";
    var row = "<tbody id='" + row_id + "'><tr>" + english_td + "</tr></tbody>";
    $("#quiz_table").prepend(row);


    fade_in(row_id);

}
