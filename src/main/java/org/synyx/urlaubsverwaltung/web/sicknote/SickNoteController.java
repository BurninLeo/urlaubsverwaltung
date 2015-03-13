package org.synyx.urlaubsverwaltung.web.sicknote;

import com.google.common.base.Optional;

import org.joda.time.DateMidnight;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;

import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.synyx.urlaubsverwaltung.core.application.domain.VacationType;
import org.synyx.urlaubsverwaltung.core.calendar.OwnCalendarService;
import org.synyx.urlaubsverwaltung.core.person.Person;
import org.synyx.urlaubsverwaltung.core.person.PersonService;
import org.synyx.urlaubsverwaltung.core.sicknote.SickNote;
import org.synyx.urlaubsverwaltung.core.sicknote.SickNoteInteractionService;
import org.synyx.urlaubsverwaltung.core.sicknote.SickNoteService;
import org.synyx.urlaubsverwaltung.core.sicknote.SickNoteType;
import org.synyx.urlaubsverwaltung.core.sicknote.comment.SickNoteComment;
import org.synyx.urlaubsverwaltung.core.sicknote.comment.SickNoteCommentService;
import org.synyx.urlaubsverwaltung.core.sicknote.comment.SickNoteStatus;
import org.synyx.urlaubsverwaltung.security.Role;
import org.synyx.urlaubsverwaltung.security.SessionService;
import org.synyx.urlaubsverwaltung.web.ControllerConstants;
import org.synyx.urlaubsverwaltung.web.DateMidnightPropertyEditor;
import org.synyx.urlaubsverwaltung.web.PersonPropertyEditor;
import org.synyx.urlaubsverwaltung.web.person.PersonConstants;
import org.synyx.urlaubsverwaltung.web.util.GravatarUtil;
import org.synyx.urlaubsverwaltung.web.validator.SickNoteConvertFormValidator;
import org.synyx.urlaubsverwaltung.web.validator.SickNoteValidator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.security.RolesAllowed;


/**
 * Controller for {@link org.synyx.urlaubsverwaltung.core.sicknote.SickNote} purposes.
 *
 * @author  Aljona Murygina - murygina@synyx.de
 */
@Controller
public class SickNoteController {

    @Autowired
    private SickNoteService sickNoteService;

    @Autowired
    private SickNoteInteractionService sickNoteInteractionService;

    @Autowired
    private SickNoteCommentService sickNoteCommentService;

    @Autowired
    private PersonService personService;

    @Autowired
    private OwnCalendarService calendarService;

    @Autowired
    private SickNoteValidator validator;

    @Autowired
    private SickNoteConvertFormValidator sickNoteConvertFormValidator;

    @Autowired
    private SessionService sessionService;

    @InitBinder
    public void initBinder(DataBinder binder) {

        binder.registerCustomEditor(DateMidnight.class, new DateMidnightPropertyEditor());
        binder.registerCustomEditor(Person.class, new PersonPropertyEditor(personService));
    }


    @RequestMapping(value = "/sicknote/{id}", method = RequestMethod.GET)
    @RolesAllowed({ "USER", "OFFICE" })
    public String sickNoteDetails(@PathVariable("id") Integer id, Model model) {

        Person loggedUser = sessionService.getLoggedUser();
        SickNote sickNote = sickNoteService.getById(id);

        if (loggedUser.hasRole(Role.OFFICE) || sickNote.getPerson().equals(loggedUser)) {
            model.addAttribute("sickNote", new ExtendedSickNote(sickNote, calendarService));
            model.addAttribute("comment", new SickNoteComment());
            model.addAttribute("gravatar", GravatarUtil.createImgURL(sickNote.getPerson().getEmail()));

            List<SickNoteComment> comments = sickNoteCommentService.getCommentsBySickNote(sickNote);
            model.addAttribute("comments", comments);

            Map<SickNoteComment, String> gravatarUrls = new HashMap<>();

            for (SickNoteComment comment : comments) {
                String gravatarUrl = GravatarUtil.createImgURL(comment.getPerson().getEmail());

                if (gravatarUrl != null) {
                    gravatarUrls.put(comment, gravatarUrl);
                }
            }

            model.addAttribute(PersonConstants.GRAVATAR_URLS, gravatarUrls);

            return "sicknote/sick_note";
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/new", method = RequestMethod.GET)
    public String newSickNote(Model model) {

        if (sessionService.isOffice()) {
            model.addAttribute("sickNote", new SickNote());
            model.addAttribute("persons", personService.getActivePersons());
            model.addAttribute("sickNoteTypes", SickNoteType.values());

            return "sicknote/sick_note_form";
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote", method = RequestMethod.POST)
    public String newSickNote(@ModelAttribute("sickNote") SickNote sickNote, Errors errors, Model model) {

        if (sessionService.isOffice()) {
            validator.validate(sickNote, errors);

            if (errors.hasErrors()) {
                model.addAttribute("errors", errors);
                model.addAttribute("sickNote", sickNote);
                model.addAttribute("persons", personService.getActivePersons());
                model.addAttribute("sickNoteTypes", SickNoteType.values());

                return "sicknote/sick_note_form";
            }

            sickNoteInteractionService.create(sickNote, sessionService.getLoggedUser());

            return "redirect:/web/sicknote/" + sickNote.getId();
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/{id}/edit", method = RequestMethod.GET)
    public String editSickNote(@PathVariable("id") Integer id, Model model) {

        SickNote sickNote = sickNoteService.getById(id);

        if (sickNote.isActive() && sessionService.isOffice()) {
            model.addAttribute("sickNote", sickNote);
            model.addAttribute("sickNoteTypes", SickNoteType.values());

            return "sicknote/sick_note_form";
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/{id}/edit", method = RequestMethod.PUT)
    public String editSickNote(@PathVariable("id") Integer id,
        @ModelAttribute("sickNote") SickNote sickNote, Errors errors, Model model) {

        if (sessionService.isOffice()) {
            validator.validate(sickNote, errors);

            if (errors.hasErrors()) {
                model.addAttribute("errors", errors);
                model.addAttribute("sickNote", sickNote);
                model.addAttribute("sickNoteTypes", SickNoteType.values());

                return "sicknote/sick_note_form";
            }

            sickNoteInteractionService.update(sickNote, sessionService.getLoggedUser());

            return "redirect:/web/sicknote/" + id;
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/{id}/comment", method = RequestMethod.POST)
    public String addComment(@PathVariable("id") Integer id,
        @ModelAttribute("comment") SickNoteComment comment, RedirectAttributes redirectAttributes, Errors errors) {

        if (sessionService.isOffice()) {
            validator.validateComment(comment, errors);

            if (errors.hasErrors()) {
                redirectAttributes.addFlashAttribute("errors", errors);
            } else {
                SickNote sickNote = sickNoteService.getById(id);
                sickNoteCommentService.create(sickNote, SickNoteStatus.COMMENTED,
                    Optional.fromNullable(comment.getText()), sessionService.getLoggedUser());
            }

            return "redirect:/web/sicknote/" + id;
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/{id}/convert", method = RequestMethod.GET)
    public String convertSickNoteToVacation(@PathVariable("id") Integer id, Model model) {

        SickNote sickNote = sickNoteService.getById(id);

        if (sickNote.isActive() && sessionService.isOffice()) {
            model.addAttribute("sickNote", new ExtendedSickNote(sickNote, calendarService));
            model.addAttribute("sickNoteConvertForm", new SickNoteConvertForm(sickNote));
            model.addAttribute("vacationTypes", VacationType.values());

            return "sicknote/sick_note_convert";
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/{id}/convert", method = RequestMethod.POST)
    public String convertSickNoteToVacation(@PathVariable("id") Integer id,
        @ModelAttribute("sickNoteConvertForm") SickNoteConvertForm sickNoteConvertForm, Errors errors, Model model) {

        if (sessionService.isOffice()) {
            SickNote sickNote = sickNoteService.getById(id);
            sickNoteConvertFormValidator.validate(sickNoteConvertForm, errors);

            if (errors.hasErrors()) {
                model.addAttribute("errors", errors);
                model.addAttribute("sickNote", new ExtendedSickNote(sickNote, calendarService));
                model.addAttribute("sickNoteConvertForm", sickNoteConvertForm);
                model.addAttribute("vacationTypes", VacationType.values());

                return "sicknote/sick_note_convert";
            }

            sickNoteInteractionService.convert(sickNote, sickNoteConvertForm.generateApplicationForLeave(),
                sessionService.getLoggedUser());

            return "redirect:/web/sicknote/" + id;
        }

        return ControllerConstants.ERROR_JSP;
    }


    @RequestMapping(value = "/sicknote/{id}/cancel", method = RequestMethod.POST)
    public String cancelSickNote(@PathVariable("id") Integer id) {

        if (sessionService.isOffice()) {
            SickNote sickNote = sickNoteService.getById(id);

            sickNoteInteractionService.cancel(sickNote, sessionService.getLoggedUser());

            return "redirect:/web/sicknote/" + id;
        }

        return ControllerConstants.ERROR_JSP;
    }
}
